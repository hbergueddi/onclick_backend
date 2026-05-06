package com.onesley.oneclick.controller.marketing;

import com.onesley.oneclick.dto.marketing.PromoNotificationRequestDto;
import com.onesley.oneclick.service.marketing.PromoNotificationRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link PromoNotificationRequestDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/promo-notification-requests")
@Tag(name = "PromoNotificationRequest", description = "Auto-generated controller for promo_notification_requests")
public class PromoNotificationRequestController {

    private final PromoNotificationRequestService service;

    public PromoNotificationRequestController(PromoNotificationRequestService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<PromoNotificationRequestDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une PromoNotificationRequest par UUID")
    public ResponseEntity<PromoNotificationRequestDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
