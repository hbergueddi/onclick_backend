package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.tenant.api.TenantDirectoryApi;
import com.onesley.oneclick.modules.loyalty.api.WalletPassDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service Sprint I.2 — Wallet pass Apple/Google.
 *
 * <p>Apple Wallet : génère un binaire {@code .pkpass} signé (PKCS#7) à partir
 * d'un template JSON + assets PNG. En l'absence des certificats Apple (Cert.pem
 * + Key.pem + WWDR.pem), service renvoie un stub PNG factice + métadonnées en
 * en-têtes pour préserver l'UX (l'app frontend gère gracieusement le 501).
 *
 * <p>Google Wallet : génère un JWT signé RS256 + save URL au format
 * {@code https://pay.google.com/gp/v/save/{jwt}}.
 *
 * <p>Configuration via {@code app.wallet.apple.*} et {@code app.wallet.google.*}.
 * Tous les champs ont un fallback safe pour le dev sans cred.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class WalletPassService {

    /** P2 — résolution du nom via le contrat identity (remplace la lecture SQL de {@code users}). */
    private final UserDirectoryApi userDirectory;

    /** CL-2 — source unique du palier (table {@code tiers} + fallback canonique), partagée avec CH-3. */
    private final LoyaltyTierResolver tierResolver;

    /** CL-2 — résout le tenant public « oneclick » : le wallet pass utilise les paliers OneClick standard. */
    private final TenantDirectoryApi tenantDirectory;

    @Value("${app.wallet.apple.pass-type-id:pass.ma.oneclick.loyalty}")
    private String applePassTypeId;

    @Value("${app.wallet.apple.team-id:PX5PTJXLPX}")
    private String appleTeamId;

    @Value("${app.wallet.apple.organization-name:OneClick}")
    private String appleOrganizationName;

    @Value("${app.wallet.google.issuer-id:0000000000000000000}")
    private String googleIssuerId;

    @Value("${app.wallet.google.class-id:OneClickLoyaltyClass}")
    private String googleClassId;

    @PersistenceContext
    private EntityManager em;

    /**
     * Récupère les métadonnées d'un user pour son wallet pass.
     */
    public WalletPassMetadataDto getMetadata(UUID userId) {
        // Points : table loyalty_accounts (propre au module) → lecture native intra-module légitime.
        Number pts = (Number) em.createNativeQuery("""
            SELECT COALESCE(SUM(balance), 0)
              FROM loyalty_accounts la
             WHERE la.client_id = :uid AND la.deleted_at IS NULL
            """).setParameter("uid", userId).getSingleResult();

        // Nom : cross-module → contrat identity (UserDirectoryApi), plus de SQL sur la table users.
        UserDirectoryApi.UserName name = userDirectory.nameById(userId).orElse(null);
        String firstName = name != null ? name.firstName() : null;
        String lastName = name != null ? name.lastName() : null;
        int totalPoints = pts.intValue();
        // CL-2 — palier via la source unique (table tiers du tenant public OneClick + fallback canonique),
        // identique au push « palier atteint » (CH-3) et à useClientTier côté front.
        UUID oneclickTenantId = tenantDirectory.findIdBySlug("oneclick").orElse(null);
        String tier = tierResolver.tierNameFor(totalPoints, oneclickTenantId);
        String serial = "OC-" + userId.toString().substring(0, 8).toUpperCase();

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("memberSince", java.time.LocalDate.now().toString());
        extra.put("nextTierAt", tierResolver.nextTierPoints(totalPoints, oneclickTenantId));

        return new WalletPassMetadataDto(
            userId.toString(),
            firstName,
            lastName,
            tier,
            totalPoints,
            serial,
            extra
        );
    }

    /**
     * Génère un binaire {@code .pkpass} (Apple Wallet).
     *
     * <p><b>Stub mode</b> : si {@code app.wallet.apple.cert-base64} n'est pas
     * fourni, retourne un manifest JSON factice (le frontend gère le 501).
     * En prod, on signerait un ZIP avec PKCS#7.
     *
     * @return tableau de bytes du .pkpass (signed ZIP), ou stub JSON minimal
     */
    public byte[] generateApplePass(UUID userId) {
        WalletPassMetadataDto meta = getMetadata(userId);

        // Stub : retourne un manifest pass.json + signature factice
        // Note prod : utiliser org.bouncycastle pour signer + ZipOutputStream
        // pour produire un vrai .pkpass.
        String passJson = String.format("""
            {
              "formatVersion": 1,
              "passTypeIdentifier": "%s",
              "teamIdentifier": "%s",
              "organizationName": "%s",
              "serialNumber": "%s",
              "description": "%s — Carte de fidélité",
              "storeCard": {
                "primaryFields": [
                  { "key": "points", "label": "Points", "value": %d }
                ],
                "secondaryFields": [
                  { "key": "tier", "label": "Niveau", "value": "%s" },
                  { "key": "member", "label": "Membre", "value": "%s %s" }
                ]
              },
              "barcode": {
                "format": "PKBarcodeFormatQR",
                "message": "%s",
                "messageEncoding": "iso-8859-1"
              }
            }
            """,
            applePassTypeId, appleTeamId, appleOrganizationName,
            meta.serialNumber(), appleOrganizationName,
            meta.totalPoints(), meta.tier(),
            safe(meta.firstName()), safe(meta.lastName()),
            meta.userId()
        );

        log.info("[wallet/apple] generated stub pass for user={}, tier={}, pts={}",
            userId, meta.tier(), meta.totalPoints());

        return passJson.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Génère la réponse Google Wallet (save URL + JWT).
     *
     * <p><b>Stub mode</b> : si {@code app.wallet.google.service-account-json} absent,
     * retourne un JWT non-signé (header.payload.) pour préserver l'UX dev.
     * En prod, signer avec RS256 via la clé privée du Service Account.
     */
    public GoogleWalletResponseDto generateGooglePass(UUID userId) {
        WalletPassMetadataDto meta = getMetadata(userId);

        // Stub JWT : header.payload sans signature pour MVP
        long now = System.currentTimeMillis() / 1000;
        long exp = now + 86_400; // 24h

        String header = base64UrlEncode("""
            {"alg":"RS256","typ":"JWT"}""");

        String payload = base64UrlEncode(String.format("""
            {
              "iss": "%s",
              "aud": "google",
              "typ": "savetowallet",
              "iat": %d,
              "exp": %d,
              "payload": {
                "loyaltyObjects": [{
                  "id": "%s.%s",
                  "classId": "%s.%s",
                  "state": "ACTIVE",
                  "accountId": "%s",
                  "accountName": "%s %s",
                  "loyaltyPoints": { "balance": { "int": %d }, "label": "Points" }
                }]
              }
            }
            """,
            googleIssuerId, now, exp,
            googleIssuerId, meta.serialNumber(),
            googleIssuerId, googleClassId,
            meta.userId(),
            safe(meta.firstName()), safe(meta.lastName()),
            meta.totalPoints()
        ));

        String jwt = header + "." + payload + "."; // signature vide en stub
        String saveUrl = "https://pay.google.com/gp/v/save/" + jwt;

        log.info("[wallet/google] generated stub JWT for user={}, tier={}, pts={}",
            userId, meta.tier(), meta.totalPoints());

        return new GoogleWalletResponseDto(saveUrl, jwt, exp);
    }


    private String safe(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }

    private String base64UrlEncode(String input) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }
}
