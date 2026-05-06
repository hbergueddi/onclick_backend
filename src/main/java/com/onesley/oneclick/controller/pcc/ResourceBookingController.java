package com.onesley.oneclick.controller.pcc;

import com.onesley.oneclick.dto.pcc.ResourceBookingDto;
import com.onesley.oneclick.service.pcc.ResourceBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link ResourceBookingDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasAnyRole('admin','restaurateur','client','tenant_admin').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/resource-bookings")
@Tag(name = "ResourceBooking", description = "Auto-generated controller for resource_bookings")
@PreAuthorize("hasAnyRole('admin','restaurateur','client','tenant_admin')")
public class ResourceBookingController {

    private final ResourceBookingService service;

    public ResourceBookingController(ResourceBookingService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<ResourceBookingDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une ResourceBooking par UUID")
    public ResponseEntity<ResourceBookingDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
