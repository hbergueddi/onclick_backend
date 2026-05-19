package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.core.notification.api.PromoNotificationDtos.*;
import com.onesley.oneclick.core.notification.internal.PromoNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Bug 32 (Batch D RBAC v2) — double-binding hasAnyRole(...) or hasAuthority('VERB:NOTIFICATIONS')
 */
@RestController
@RequestMapping("/api/notifications/promo-requests")
@Tag(name = "Promo notification requests", description = "Sprint H — workflow admin approval push promo")
public class PromoNotificationController {

    private final PromoNotificationService service;

    public PromoNotificationController(PromoNotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste des demandes push promo (admin)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR') or hasAuthority('VIEW:NOTIFICATIONS')")
    public List<PromoRequestDto> findAll(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID restaurantId
    ) {
        return service.findAll(status, restaurantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated() or hasAuthority('VIEW:NOTIFICATIONS')")
    public PromoRequestDto findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    @Operation(summary = "Restaurateur soumet une demande push promo")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR') or hasAuthority('CREATE:NOTIFICATIONS')")
    public ResponseEntity<PromoRequestDto> create(@Valid @RequestBody PromoRequestCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PatchMapping("/{id}/review")
    @Operation(summary = "Admin approve/refuse une demande push promo")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN') or hasAuthority('UPDATE:NOTIFICATIONS')")
    public PromoRequestDto review(@PathVariable UUID id, @Valid @RequestBody PromoRequestReviewDto dto) {
        return service.review(id, dto);
    }

    @PatchMapping("/{id}/sent")
    @Operation(summary = "Marque la demande comme envoyée (interne, après FCM push)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN') or hasAuthority('UPDATE:NOTIFICATIONS')")
    public PromoRequestDto markSent(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int sentCount,
        @RequestParam(required = false) String error
    ) {
        return service.markSent(id, sentCount, error);
    }

    @GetMapping("/stats")
    @Operation(summary = "Stats globales promo (offers, impressions, redemptions, push)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR') or hasAuthority('VIEW:NOTIFICATIONS')")
    public PromoStatsDto stats() { return service.stats(); }
}
