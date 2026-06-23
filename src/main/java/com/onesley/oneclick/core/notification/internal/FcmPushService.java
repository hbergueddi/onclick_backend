package com.onesley.oneclick.core.notification.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushReservationDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushResultDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM push service — port des Edge Functions Supabase {@code send-promo-push}
 * et {@code send-reservation-push} (Sprint B.8.4).
 *
 * <p><b>Mode stub</b> si {@code app.fcm.project-id} ou {@code app.fcm.service-account-json}
 * ne sont pas définis (dev local / CI sans secret Firebase) : aucun appel réseau,
 * log + {@code PushResultDto{ sent: 0, message: "FCM not configured" }}.
 *
 * <p><b>Mode actif</b> (B.8.4) : signature JWT RS256 du service-account → access token
 * Google OAuth2 → fan-out FCM HTTP v1 ({@code projects/{projectId}/messages:send}),
 * un appel par token. Les tokens périmés (FCM {@code UNREGISTERED}/{@code NOT_FOUND})
 * sont supprimés de {@code device_tokens} (cleanup, parité Deno legacy).
 *
 * <p>Activation prod : {@code FCM_PROJECT_ID=oneclick-129bb} +
 * {@code FCM_SERVICE_ACCOUNT_JSON=<JSON service-account complet>}.
 */
@Service
@Slf4j
public class FcmPushService {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String FCM_SEND_URL =
        "https://fcm.googleapis.com/v1/projects/{projectId}/messages:send";

    private final String projectId;
    private final String serviceAccountJson;
    private final boolean dispatchEnabled;
    private final DeviceTokenRepository tokenRepo;
    private final NotificationRepository notifRepo;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * {@code RestClient} construit depuis le {@code Builder} Spring auto-configuré
     * (injectable en test via {@code MockRestServiceServer}, aligné sur {@code ResendClient}).
     */
    public FcmPushService(
        @Value("${app.fcm.project-id:}") String projectId,
        @Value("${app.fcm.service-account-json:}") String serviceAccountJson,
        @Value("${app.fcm.dispatch-enabled:false}") boolean dispatchEnabled,
        RestClient.Builder restClientBuilder,
        DeviceTokenRepository tokenRepo,
        NotificationRepository notifRepo
    ) {
        this.projectId = projectId;
        this.serviceAccountJson = serviceAccountJson;
        this.dispatchEnabled = dispatchEnabled;
        this.restClient = restClientBuilder.build();
        this.tokenRepo = tokenRepo;
        this.notifRepo = notifRepo;
    }

    /** {@code true} si {@code app.fcm.*} (project-id + service-account-json) sont définis. */
    public boolean isConfigured() {
        return projectId != null && !projectId.isBlank()
            && serviceAccountJson != null && !serviceAccountJson.isBlank();
    }

    /**
     * Raison du mode stub, ou {@code null} si l'envoi réel est actif. Deux verrous :
     * (1) clés FCM présentes ({@link #isConfigured()}), (2) flag {@code app.fcm.dispatch-enabled}.
     * Le flag est le <b>garde-fou</b> safe-by-default (cf. application.yml) : un backend dev avec
     * des clés valides reste en stub tant que {@code FCM_DISPATCH_ENABLED=true} n'est pas posé —
     * empêche les dispatches dormants (events Modulith republiés au restart) de partir vers de
     * vrais appareils.
     */
    private String stubReason() {
        if (!isConfigured()) return "FCM not configured";
        if (!dispatchEnabled) return "FCM dispatch disabled (app.fcm.dispatch-enabled=false)";
        return null;
    }

    /** Push promo aux device_tokens de tous les {@code userIds} (port {@code send-promo-push}). */
    public PushResultDto sendPromo(PushPromoDto dto) {
        List<DeviceToken> tokens = new ArrayList<>();
        for (UUID userId : dto.userIds()) {
            tokens.addAll(tokenRepo.findAllByUserId(userId));
        }
        String stub = stubReason();
        if (stub != null) {
            log.warn("[fcm-push] promo — {} → skip (total tokens={}, campaign={})",
                stub, tokens.size(), dto.campaignId());
            return new PushResultDto(0, tokens.size(), List.of(), stub);
        }
        return fanOutFcm(tokens, dto.title(), dto.body(), dto.link());
    }

    /** Push résa au recipient (client OU staff selon status) — port {@code send-reservation-push}. */
    public PushResultDto sendReservation(PushReservationDto dto) {
        List<DeviceToken> tokens = tokenRepo.findAllByUserId(dto.recipientUserId());
        String stub = stubReason();
        if (stub != null) {
            log.warn("[fcm-push] reservation — {} → skip (recipient={}, reservation={}, status={}, tokens={})",
                stub, dto.recipientUserId(), dto.reservationId(), dto.status(), tokens.size());
            return new PushResultDto(0, tokens.size(), List.of(), stub);
        }
        return fanOutFcm(tokens, dto.title(), dto.body(), dto.link());
    }

    /** Fan-out FCM HTTP v1 — 1 access token OAuth puis 1 appel par device token. */
    private PushResultDto fanOutFcm(List<DeviceToken> tokens, String title, String body, String link) {
        final String accessToken;
        try {
            accessToken = getAccessToken();
        } catch (Exception e) {
            log.error("[fcm-push] échec acquisition du token OAuth FCM: {}", e.getMessage(), e);
            return new PushResultDto(0, tokens.size(), List.of("oauth: " + e.getMessage()), "FCM auth failed");
        }

        int sent = 0;
        List<String> failures = new ArrayList<>();
        for (DeviceToken t : tokens) {
            try {
                sendOne(accessToken, t, title, body, link);
                sent++;
            } catch (RestClientResponseException e) {
                String resp = e.getResponseBodyAsString();
                // Token périmé = signal FCM canonique : HTTP 404 + errorCode UNREGISTERED
                // (ou status NOT_FOUND). On NE purge PAS sur un simple 404 proxy/gateway ni sur
                // une sous-chaîne du corps (un 400 INVALID_ARGUMENT mentionnant « not found » ne
                // doit jamais supprimer un token valide). cf. revue adversariale B.8.4 (A3).
                if (e.getStatusCode().value() == 404 && isUnregistered(resp)) {
                    // Cleanup best-effort : supprimer un token périmé ne doit JAMAIS faire échouer le
                    // fan-out. Sinon, appelé depuis une transaction async (ex PromoDispatchListener),
                    // une exception de delete ferait planter le listener Modulith → event incomplet
                    // (rejoué en boucle au restart) + demande coincée. cf. fix résilience promo-push.
                    try {
                        tokenRepo.delete(t);   // app désinstallée / token renouvelé → cleanup
                        failures.add(t.getId() + ": UNREGISTERED (cleaned)");
                        log.info("[fcm-push] token périmé supprimé id={}", t.getId());
                    } catch (Exception delEx) {
                        failures.add(t.getId() + ": UNREGISTERED (cleanup failed)");
                        log.warn("[fcm-push] suppression token périmé id={} échouée : {}",
                            t.getId(), delEx.getMessage());
                    }
                } else {
                    failures.add(t.getId() + ": HTTP " + e.getStatusCode().value());
                    log.warn("[fcm-push] échec FCM token id={} status={} body={}",
                        t.getId(), e.getStatusCode().value(), resp);
                }
            } catch (Exception e) {
                // On n'expose pas le token (credential push) dans la réponse : l'id suffit à corréler.
                failures.add(t.getId() + ": " + e.getMessage());
                log.warn("[fcm-push] échec envoi token id={}: {}", t.getId(), e.getMessage());
            }
        }
        return new PushResultDto(sent, tokens.size(), failures,
            sent == tokens.size() ? "ok" : "sent " + sent + "/" + tokens.size());
    }

    /**
     * JWT RS256 service-account → access token Google OAuth2 (scope {@code firebase.messaging}).
     * Package-private pour test unitaire isolé. Parse {@code client_email}/{@code private_key}/
     * {@code token_uri} du JSON, signe un JWT (Nimbus) et l'échange via {@code grant_type=jwt-bearer}.
     */
    String getAccessToken() throws Exception {
        JsonNode sa = objectMapper.readTree(serviceAccountJson);
        String clientEmail = sa.get("client_email").asText();
        String tokenUri = sa.hasNonNull("token_uri") ? sa.get("token_uri").asText() : DEFAULT_TOKEN_URI;
        RSAPrivateKey privateKey = parsePrivateKey(sa.get("private_key").asText());

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(clientEmail)
            .audience(tokenUri)
            .claim("scope", SCOPE)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(3600)))
            .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        JWSSigner signer = new RSASSASigner(privateKey);
        jwt.sign(signer);

        String form = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + jwt.serialize();
        // Lecture en String (StringHttpMessageConverter, sans Jackson) puis parsing avec notre
        // ObjectMapper Jackson 2 : le converter par défaut de Spring 7 est Jackson 3
        // (tools.jackson) et ne sait pas construire un JsonNode Jackson 2 via .body(JsonNode.class).
        String json = restClient.post()
            .uri(tokenUri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(String.class);
        JsonNode resp = (json == null || json.isBlank()) ? null : objectMapper.readTree(json);
        if (resp == null || !resp.hasNonNull("access_token")) {
            throw new IllegalStateException("réponse OAuth sans access_token");
        }
        return resp.get("access_token").asText();
    }

    /**
     * {@code true} si le corps d'erreur FCM dénote un token réellement périmé :
     * {@code error.details[].errorCode == "UNREGISTERED"} ou {@code error.status == "NOT_FOUND"}.
     * Renvoie {@code false} pour un corps non-JSON (404 proxy/gateway) ou tout autre code —
     * garde-fou pour ne jamais purger un token valide sur une erreur transitoire/ambiguë.
     */
    private boolean isUnregistered(String body) {
        if (body == null || body.isBlank()) return false;
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            for (JsonNode detail : error.path("details")) {
                if ("UNREGISTERED".equals(detail.path("errorCode").asText(null))) {
                    return true;
                }
            }
            return "NOT_FOUND".equals(error.path("status").asText(null));
        } catch (Exception e) {
            return false;   // corps non-JSON → pas un signal de cleanup
        }
    }

    /** PEM PKCS#8 ({@code -----BEGIN PRIVATE KEY-----}) → {@link RSAPrivateKey}. */
    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** POST FCM HTTP v1 pour un token (lève {@link RestClientResponseException} sur 4xx/5xx). */
    private void sendOne(String accessToken, DeviceToken token, String title, String body, String link) {
        Map<String, Object> message = new HashMap<>();
        message.put("token", token.getToken());
        message.put("notification", Map.of("title", title, "body", body));
        if (link != null && !link.isBlank()) {
            message.put("data", Map.of("link", link));   // FCM data : valeurs String uniquement
        }
        restClient.post()
            .uri(FCM_SEND_URL, projectId)
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("message", message))
            .retrieve()
            .toBodilessEntity();
    }
}
