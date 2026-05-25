package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.modules.loyalty.api.WalletPassDtos.*;
import com.onesley.oneclick.modules.loyalty.internal.WalletPassService;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints Wallet pass — Sprint I.2.
 *
 * <p>Apple : {@code GET /api/loyalty/wallet-pass?platform=apple} → renvoie
 * un binaire {@code application/vnd.apple.pkpass} (téléchargement direct).
 *
 * <p>Google : {@code GET /api/loyalty/wallet-pass?platform=google} → renvoie
 * un JSON avec {@code save_url} + JWT pour le bouton "Add to Wallet".
 *
 * <p>Métadonnées seules : {@code GET /api/loyalty/wallet-pass/metadata}.
 */
@RestController
@RequestMapping("/api/loyalty/wallet-pass")
@Tag(name = "Wallet pass", description = "Sprint I.2 — Apple .pkpass + Google Wallet save URL")
@RequiredArgsConstructor
public class WalletPassController {

    private final WalletPassService service;

    // Bug 32 (Batch A RBAC v2) — RBAC v2 senior strict VIEW:LOYALTY (génération = lecture).
    @GetMapping
    @Operation(summary = "Génère le wallet pass pour l'utilisateur courant",
        description = "Apple : binaire .pkpass | Google : JSON { save_url, jwt }")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public ResponseEntity<?> generate(
        @RequestParam(defaultValue = "apple") String platform,
        @Parameter(hidden = true) @RequestParam(required = false) UUID userId
    ) {
        // userId peut venir du JWT en prod (extraction via SecurityContext).
        // En dev/MVP : on accepte un param explicite pour faciliter le test.
        UUID effectiveUid = resolveEffectiveUid(userId);

        return switch (platform.toLowerCase()) {
            case "apple" -> {
                byte[] pkpass = service.generateApplePass(effectiveUid);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType("application/vnd.apple.pkpass"));
                headers.setContentDispositionFormData("attachment", "oneclick.pkpass");
                yield ResponseEntity.ok().headers(headers).body(pkpass);
            }
            case "google" -> {
                GoogleWalletResponseDto resp = service.generateGooglePass(effectiveUid);
                yield ResponseEntity.ok(resp);
            }
            default -> ResponseEntity.badRequest().body(
                "{\"error\":\"platform doit être 'apple' ou 'google'\"}"
            );
        };
    }

    @GetMapping("/metadata")
    @Operation(summary = "Métadonnées wallet (tier + points + nom)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public WalletPassMetadataDto metadata(@RequestParam(required = false) UUID userId) {
        UUID effectiveUid = resolveEffectiveUid(userId);
        return service.getMetadata(effectiveUid);
    }

    /**
     * P2 owner-check : le wallet pass est personnel. Un non-admin ne peut générer
     * QUE son propre pass ; seul un admin peut cibler un autre {@code userId}.
     * Avant : le param {@code userId} écrasait l'identité courante sans contrôle
     * (VIEW:LOYALTY détenu par CLIENT → lecture du pass d'autrui).
     */
    private UUID resolveEffectiveUid(UUID requested) {
        UUID self = resolveCurrentUserId();
        if (requested == null || requested.equals(self)) return self;
        if (SecurityHelper.isAdmin()) return requested;
        throw new ForbiddenException("Wallet pass : accès limité à votre propre compte");
    }

    /**
     * Extrait l'userId courant du SecurityContext (JWT). Fallback : exception
     * 401 si pas authentifié.
     */
    private UUID resolveCurrentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new com.onesley.oneclick.exception.UnauthorizedException(
                "Identité utilisateur manquante");
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new com.onesley.oneclick.exception.UnauthorizedException(
                "Identité utilisateur invalide: " + auth.getName());
        }
    }
}
