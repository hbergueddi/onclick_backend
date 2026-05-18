package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.modules.loyalty.api.LoyaltyExtensionDtos.*;
import com.onesley.oneclick.modules.loyalty.internal.LoyaltyExtensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints Sprint H — extensions admin/whitelabel pour le module loyalty.
 *
 * <p>Split du LoyaltyController principal pour garder une responsabilité claire :
 * ce contrôleur ne gère que les vues admin (rating, scores, AI usage, restitutions,
 * expired points admin, point distributions, restaurant tier status).
 */
@RestController
@RequestMapping("/api/loyalty")
@Tag(name = "Loyalty extensions", description = "Sprint H — admin views (ratings, AI usage, restitutions, expired points, tier)")
public class LoyaltyExtensionController {

    private final LoyaltyExtensionService service;

    public LoyaltyExtensionController(LoyaltyExtensionService service) {
        this.service = service;
    }

    // ─── Client ratings ─────────────────────────────────────────────────
    @GetMapping("/ratings/by-user/{userId}")
    @Operation(summary = "Liste des ratings client (visible_rating + history)")
    @PreAuthorize("isAuthenticated()")
    public List<ClientRatingDto> findUserRatings(@PathVariable UUID userId) {
        return service.findUserRatings(userId);
    }

    @GetMapping("/scores/by-user/{userId}")
    @Operation(summary = "Score agrégé (avg rating × 20 → /100)")
    @PreAuthorize("isAuthenticated()")
    public ClientScoreDto computeUserScore(@PathVariable UUID userId) {
        return service.computeUserScore(userId);
    }

    public record RatingRecordDto(@NotNull UUID userId, UUID reservationId, @NotNull BigDecimal delta, String reason) {}

    @PostMapping("/ratings")
    @Operation(summary = "Enregistre un rating delta (+0.1 honorée, -0.5 no_show, etc.)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ClientRatingDto recordRating(@Valid @RequestBody RatingRecordDto dto) {
        return service.recordRating(dto.userId(), dto.reservationId(), dto.delta(), dto.reason());
    }

    // ─── AI usage ────────────────────────────────────────────────────────
    @GetMapping("/ai-usage/by-user/{userId}")
    @Operation(summary = "Usage AI (rate limit 20/day window 24h)")
    @PreAuthorize("isAuthenticated()")
    public AIUsageDto findUsage(@PathVariable UUID userId) {
        return service.findUsage(userId);
    }

    @PostMapping("/ai-usage/by-user/{userId}/increment")
    @Operation(summary = "Increment AI usage (appelé par le frontend après chaque prompt)")
    @PreAuthorize("isAuthenticated()")
    public AIUsageDto incrementUsage(@PathVariable UUID userId) {
        return service.incrementUsage(userId);
    }

    // ─── Restitutions ─────────────────────────────────────────────────────
    @GetMapping("/restitutions/by-restaurant/{restaurantId}")
    @Operation(summary = "Restitutions de points pour un restaurant")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public List<RestaurantRestitutionDto> findRestaurantRestitutions(@PathVariable UUID restaurantId) {
        return service.findRestaurantRestitutions(restaurantId);
    }

    public record RestitutionCreateDto(@NotNull UUID restaurantId, @NotNull BigDecimal amount, Integer points, String reason) {}

    @PostMapping("/restitutions")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public RestaurantRestitutionDto createRestitution(@Valid @RequestBody RestitutionCreateDto dto) {
        return service.createRestitution(dto.restaurantId(), dto.amount(), dto.points() == null ? 0 : dto.points(), dto.reason());
    }

    // ─── Restaurant tier status ──────────────────────────────────────────
    @GetMapping("/tier-status/by-restaurant/{restaurantId}")
    @Operation(summary = "Tier actuel d'un restaurant (Standard/Bronze/Silver/Gold)")
    @PreAuthorize("isAuthenticated()")
    public RestaurantTierStatusDto getRestaurantTier(@PathVariable UUID restaurantId) {
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
    @PreAuthorize("isAuthenticated()")
    public List<ExpiredPointsAdminDto> findExpiredPointsAdmin(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(defaultValue = "200") int limit
    ) {
        return service.findExpiredPointsAdmin(restaurantId, limit);
    }

    // ─── Point distributions admin view ──────────────────────────────────
    @GetMapping("/point-distributions")
    @Operation(summary = "Vue admin des distributions de points (loyalty_transactions where amount > 0)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public List<PointDistributionDto> findPointDistributions(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) UUID userId,
        @RequestParam(defaultValue = "200") int limit
    ) {
        return service.findPointDistributions(restaurantId, userId, limit);
    }
}
