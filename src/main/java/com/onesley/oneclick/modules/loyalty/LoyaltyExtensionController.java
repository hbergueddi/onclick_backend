package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.modules.loyalty.api.LoyaltyExtensionDtos.*;
import com.onesley.oneclick.modules.loyalty.api.RedemptionDto;
import com.onesley.oneclick.modules.loyalty.internal.LoyaltyExtensionService;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints Sprint H — extensions admin/whitelabel pour le module loyalty.
 *
 * <p>Split du LoyaltyController principal pour garder une responsabilité claire :
 * ce contrôleur ne gère que les vues admin (rating, scores, AI usage, restitutions,
 * expired points admin, point distributions, restaurant tier status).
 *
 * <p>Bug 32 RBAC v2 — pattern senior strict {@code hasAuthority('VERB:RESOURCE')} sur tous les endpoints.
 */
@RestController
@RequestMapping("/api/loyalty")
@Tag(name = "Loyalty extensions", description = "Sprint H — admin views (ratings, AI usage, restitutions, expired points, tier)")
@RequiredArgsConstructor
public class LoyaltyExtensionController {

    private final LoyaltyExtensionService service;
    private final RestaurantAccessGuard restaurantAccessGuard;

    // ─── Client ratings ─────────────────────────────────────────────────
    @GetMapping("/ratings/by-user/{userId}")
    @Operation(summary = "Liste des ratings client (visible_rating + history)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<ClientRatingDto> findUserRatings(@PathVariable UUID userId) {
        // Réputation client : self OU staff qui note les clients (CREATE:LOYALTY) OU admin.
        // ProDesk affiche la fiabilité d'un client (y.c. nouveau) avant de confirmer une résa.
        SecurityHelper.requireSelfOrAuthorityOrAdmin(userId, "CREATE:LOYALTY");
        return service.findUserRatings(userId);
    }

    @GetMapping("/scores/by-user/{userId}")
    @Operation(summary = "Score agrégé (avg rating × 20 → /100)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public ClientScoreDto computeUserScore(@PathVariable UUID userId) {
        // Réputation client : self OU staff qui note les clients (CREATE:LOYALTY) OU admin.
        SecurityHelper.requireSelfOrAuthorityOrAdmin(userId, "CREATE:LOYALTY");
        return service.computeUserScore(userId);
    }

    // ─── Configuration du moteur de notation (singleton, admin-only) ──────
    @GetMapping("/score-config")
    @Operation(summary = "Config singleton de notation client (seuils + règles de calcul)")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public ClientScoreConfigDto getScoreConfig() {
        return service.getScoreConfig();
    }

    @PatchMapping("/score-config")
    @Operation(summary = "Met à jour la config de notation client (partiel)")
    @PreAuthorize("hasAuthority('UPDATE:ANALYTICS')")
    public ClientScoreConfigDto updateScoreConfig(@Valid @RequestBody ClientScoreConfigPatchDto dto) {
        return service.updateScoreConfig(dto);
    }

    // ─── Distribution des membres par palier (vue admin /fidelite) ────────
    @GetMapping("/tier-distribution")
    @Operation(summary = "Nombre de clients par palier de fidélité (points globaux bucketés)")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public List<TierDistributionDto> tierDistribution() {
        return service.tierDistribution();
    }

    public record RatingRecordDto(@NotNull UUID userId, UUID reservationId, @NotNull BigDecimal delta, String reason) {}

    @PostMapping("/ratings")
    @Operation(summary = "Enregistre un rating delta (+0.1 honorée, -0.5 no_show, etc.)")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public ClientRatingDto recordRating(@Valid @RequestBody RatingRecordDto dto) {
        return service.recordRating(dto.userId(), dto.reservationId(), dto.delta(), dto.reason());
    }

    // ─── AI usage ────────────────────────────────────────────────────────
    @GetMapping("/ai-usage/by-user/{userId}")
    @Operation(summary = "Usage AI (rate limit 20/day window 24h)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public AIUsageDto findUsage(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findUsage(userId);
    }

    @PostMapping("/ai-usage/by-user/{userId}/increment")
    @Operation(summary = "Increment AI usage (appelé par le frontend après chaque prompt)")
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY')")
    public AIUsageDto incrementUsage(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.incrementUsage(userId);
    }

    // ─── Restitutions ─────────────────────────────────────────────────────
    @GetMapping("/restitutions/by-restaurant/{restaurantId}")
    @Operation(summary = "Restitutions de points pour un restaurant")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<RestaurantRestitutionDto> findRestaurantRestitutions(@PathVariable UUID restaurantId) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.findRestaurantRestitutions(restaurantId);
    }

    @GetMapping("/restitutions/by-restaurants")
    @Operation(
        summary = "Restitutions de plusieurs restaurants en UNE requête (B1.5 — anti N+1)",
        description = "Remplace le fan-out N+1 de useRestitutions (1 appel par resto du pool). " +
                      "VIEW:LOYALTY + ABAC : admin → tout ; sinon staff actif de CHAQUE resto demandé."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<RestaurantRestitutionDto> findRestitutionsByRestaurants(
        @RequestParam @Size(max = 1000) List<UUID> restaurantIds
    ) {
        restaurantIds.forEach(restaurantAccessGuard::requireAdminOrActiveStaffOf);
        return service.findRestitutionsByRestaurants(restaurantIds);
    }

    public record RestitutionCreateDto(@NotNull UUID restaurantId, @NotNull BigDecimal amount, Integer points, String reason) {}

    @PostMapping("/restitutions")
    // Restitution financière = gérant/admin → UPDATE:LOYALTY (pas CREATE:LOYALTY,
    // détenu aussi par STAFF pour le Snap2Earn).
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY')")
    public RestaurantRestitutionDto createRestitution(@Valid @RequestBody RestitutionCreateDto dto) {
        return service.createRestitution(dto.restaurantId(), dto.amount(), dto.points() == null ? 0 : dto.points(), dto.reason());
    }

    @GetMapping("/restitutions")
    @Operation(
        summary = "Audit paginé des restitutions resto (/forge/restitutions) — filtres restaurantId / fenêtre de dates",
        description = "VIEW:LOYALTY + ABAC : admin (SUPERADMIN/GROUP_ADMIN) → toutes ; owner (RESTAURATEUR/STAFF) "
                    + "→ uniquement ses restaurants (sinon page vide / 403 si restaurantId hors périmètre). "
                    + "restaurantName résolu serveur-side (read-view). Bornes [from, to[ sur created_at (ISO-8601)."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public PageResponse<RestaurantRestitutionDto> listRestitutionsAudit(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        // restaurantId explicite + non-admin → 403 si l'appelant n'est pas staff de CE resto.
        // restaurantId absent → le service scope automatiquement (owner = ses restos ; client = vide).
        if (restaurantId != null && !SecurityHelper.isAdmin()) {
            restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        }
        return service.findRestitutionsAudit(restaurantId, from, to, page, size);
    }

    // ─── Redemptions audit (Forge — /forge/redemptions) ─────────────────────
    @GetMapping("/redemptions")
    @Operation(
        summary = "Audit paginé des rédemptions de points (/forge/redemptions) — filtres restaurantId / dates / statut OTP",
        description = "VIEW:LOYALTY + ABAC : admin → toutes ; owner → uniquement ses restaurants ; client → vide. "
                    + "client/restaurant résolus via JOIN loyalty_accounts ; noms enrichis serveur-side (anti-N+1). "
                    + "status = 'otp_validated' | 'standard' (la table redemptions n'a pas de statut accepté/refusé). "
                    + "Bornes [from, to[ sur created_at (ISO-8601)."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public PageResponse<RedemptionDto> listRedemptionsAudit(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        if (restaurantId != null && !SecurityHelper.isAdmin()) {
            restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        }
        return service.findRedemptionsAudit(restaurantId, from, to, status, page, size);
    }

    // ─── Restaurant tier status ──────────────────────────────────────────
    @GetMapping("/tier-status/by-restaurant/{restaurantId}")
    @Operation(summary = "Tier actuel d'un restaurant (Standard/Bronze/Silver/Gold)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public RestaurantTierStatusDto getRestaurantTier(@PathVariable UUID restaurantId) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.getRestaurantTier(restaurantId);
    }

    // ─── Expired points admin view ───────────────────────────────────────
    @GetMapping("/expired-points/admin")
    @Operation(
        summary = "Vue admin des points expirés (filter optionnel par resto)",
        description = "RBAC : SUPERADMIN/GROUP_ADMIN voient tout. RESTAURATEUR/STAFF "
                    + "peuvent voir UNIQUEMENT leur propre restaurant (restaurantId "
                    + "obligatoire + check staff actif dans le service)."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<ExpiredPointsAdminDto> findExpiredPointsAdmin(
        @RequestParam(required = false) UUID restaurantId,
        // Borne dure : évite les pulls non bornés (Spring Boot 4 valide les params sans @Validated).
        @RequestParam(defaultValue = "200") @Min(1) @Max(5000) int limit
    ) {
        return service.findExpiredPointsAdmin(restaurantId, limit);
    }

    // ─── Point distributions admin view ──────────────────────────────────
    @GetMapping("/point-distributions")
    @Operation(summary = "Vue admin des distributions de points (loyalty_transactions where amount > 0)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<PointDistributionDto> findPointDistributions(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) UUID userId,
        // Borne haute = 10000 : plafond de l'export CSV admin (le plus gros consommateur
        // légitime). Ferme le vecteur de pull non borné sans régresser l'export.
        @RequestParam(defaultValue = "200") @Min(1) @Max(10000) int limit
    ) {
        // Vue admin : non-admin doit scoper à un restaurant dont il est staff actif.
        if (!SecurityHelper.isAdmin()) {
            restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        }
        return service.findPointDistributions(restaurantId, userId, limit);
    }

    // ─── Économie de points (agrégat plateforme) — page admin OneClick Lounge ──
    @GetMapping("/points-economy")
    @Operation(
        summary = "Agrégat plateforme de l'économie de points (émis/consommé/expiré/disponible + type + tendance 6 mois)",
        description = "Cross-restaurant : réservé admin (VIEW:ANALYTICS), comme les autres agrégats " +
                      "plateforme du PulseBoard. Calcul serveur sur loyalty_transactions (earn/spend/expire)."
    )
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public PointsEconomyDto pointsEconomy() {
        return service.getPointsEconomy();
    }

    // ─── Agrégat crédit d'un restaurant (fiche resto — onglets Clients/Staff) ──
    @GetMapping("/restaurant-credit-summary/{restaurantId}")
    @Operation(
        summary = "Agrégat crédit d'un restaurant (accordé/consommé/dispo + crédits par membre)",
        description = "Calcul serveur-side : remplace le pull de 10 000 lignes que faisaient " +
                      "ClientSummary/StaffSummary. VIEW:LOYALTY + ABAC (admin ou staff actif du resto)."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public RestaurantCreditSummaryDto restaurantCreditSummary(@PathVariable UUID restaurantId) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.restaurantCreditSummary(restaurantId);
    }
}
