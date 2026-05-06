package com.onesley.oneclick.controller.reservation;

import com.onesley.oneclick.dto.reservation.ReservationDto;
import com.onesley.oneclick.entity.reservation.ReservationStatus;
import com.onesley.oneclick.service.reservation.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Réservations couverts (workflow demandée → confirmée → honorée)")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une réservation")
    public ResponseEntity<ReservationDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-client")
    @Operation(summary = "Réservations d'un client (Mes réservations)")
    public List<ReservationDto> findByClient(@RequestParam UUID clientId) {
        return service.findByClient(clientId);
    }

    @GetMapping("/by-restaurant")
    @Operation(summary = "Réservations d'un restaurant à une date donnée (calendrier ProDesk)")
    public List<ReservationDto> findByRestaurantAndDate(
        @RequestParam UUID restaurantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.findByRestaurantAndDate(restaurantId, date);
    }

    @GetMapping("/by-status")
    @Operation(summary = "Réservations en cours par statut (admin)")
    public List<ReservationDto> findByStatus(@RequestParam ReservationStatus status) {
        return service.findByStatus(status);
    }
}
