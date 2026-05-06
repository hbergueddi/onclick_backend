package com.onesley.oneclick.controller.reservation;

import com.onesley.oneclick.dto.reservation.ReservationGuestDto;
import com.onesley.oneclick.service.reservation.ReservationGuestService;
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
 * REST controller pour {@link ReservationGuestDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasAnyRole('admin','restaurateur','client').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/reservation-guests")
@Tag(name = "ReservationGuest", description = "Auto-generated controller for reservation_guests")
@PreAuthorize("hasAnyRole('admin','restaurateur','client')")
public class ReservationGuestController {

    private final ReservationGuestService service;

    public ReservationGuestController(ReservationGuestService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<ReservationGuestDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une ReservationGuest par UUID")
    public ResponseEntity<ReservationGuestDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
