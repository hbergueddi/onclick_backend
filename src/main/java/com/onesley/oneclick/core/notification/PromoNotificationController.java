package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.core.notification.api.PromoNotificationDtos.*;
import com.onesley.oneclick.core.notification.internal.PromoNotificationService;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Bug 32 (Batch D RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:NOTIFICATIONS')
 */
@RestController
@RequestMapping("/api/notifications/promo-requests")
@Tag(name = "Promo notification requests", description = "Sprint H — workflow admin approval push promo")
@RequiredArgsConstructor
public class PromoNotificationController {

    private final PromoNotificationService service;
    private final RestaurantAccessGuard restaurantAccessGuard;

    @GetMapping
    @Operation(summary = "Liste des demandes push promo (admin = toutes ; restaurateur = son resto)")
    @PreAuthorize("hasAuthority('VIEW:NOTIFICATIONS')")
    public List<PromoRequestDto> findAll(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID restaurantId
    ) {
        // ABAC : un non-admin ne liste que les demandes de SON restaurant
        // (admin → restaurantId optionnel, voit tout).
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.findAll(status, restaurantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW:NOTIFICATIONS')")
    public PromoRequestDto findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    @Operation(summary = "Restaurateur soumet une demande push promo pour son offre")
    // A(a) : le restaurateur (CREATE:OFFERS) demande une notif pour SON offre.
    // CREATE:NOTIFICATIONS était SUPERADMIN-only (V34) → 403 restaurateur. ABAC
    // requireAdminOrActiveStaffOf(restaurantId) ; la modération (review) reste admin.
    @PreAuthorize("hasAuthority('CREATE:OFFERS')")
    public ResponseEntity<PromoRequestDto> create(@Valid @RequestBody PromoRequestCreateDto dto) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(dto.restaurantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PatchMapping("/{id}/review")
    @Operation(summary = "Admin approve/refuse une demande push promo")
    @PreAuthorize("hasAuthority('UPDATE:NOTIFICATIONS')")
    public PromoRequestDto review(@PathVariable UUID id, @Valid @RequestBody PromoRequestReviewDto dto) {
        requireAdminModeration();
        return service.review(id, dto);
    }

    @PatchMapping("/{id}/sent")
    @Operation(summary = "Marque la demande comme envoyée (interne, après FCM push)")
    @PreAuthorize("hasAuthority('UPDATE:NOTIFICATIONS')")
    public PromoRequestDto markSent(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int sentCount,
        @RequestParam(required = false) String error
    ) {
        requireAdminModeration();
        return service.markSent(id, sentCount, error);
    }

    @GetMapping("/stats")
    @Operation(summary = "Stats globales promo (offers, impressions, redemptions, push)")
    @PreAuthorize("hasAuthority('VIEW:NOTIFICATIONS')")
    public PromoStatsDto stats() { return service.stats(); }

    /**
     * Modération promo (review / markSent) = admin (SUPERADMIN/GROUP_ADMIN) uniquement.
     * {@code UPDATE:NOTIFICATIONS} est détenu par TOUS les rôles (V34, accès cloche
     * self-service) : sans ce garde, un restaurateur approuverait sa propre demande
     * (push aux clients sans validation admin). ABAC admin-only.
     */
    private void requireAdminModeration() {
        if (!SecurityHelper.isAdmin()) {
            throw new ForbiddenException("Modération des demandes push réservée à l'administrateur");
        }
    }
}
