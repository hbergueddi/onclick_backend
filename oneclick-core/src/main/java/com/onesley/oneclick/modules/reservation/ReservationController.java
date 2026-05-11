package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Workflow réservations (§5)")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    public record StatusChangeDto(String status, UUID changedById, String reason) {}

    @GetMapping
    @Operation(summary = "Liste paginée — filtres clientId / restaurantId / status optionnels")
    public PageResponse<ReservationDto> findAll(
        @RequestParam(required = false) UUID clientId,
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAll(clientId, restaurantId, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail réservation par UUID")
    public ReservationDto findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Crée une réservation (status initial: pending)")
    public ResponseEntity<ReservationDto> create(@Valid @RequestBody ReservationCreateDto dto) {
        ReservationDto r = service.create(dto);
        return ResponseEntity.created(URI.create("/api/reservations/" + r.id())).body(r);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change le statut (workflow audit dans reservation_status_histories)")
    public ReservationDto changeStatus(@PathVariable UUID id, @RequestBody StatusChangeDto body) {
        return service.changeStatus(id, body.status(), body.changedById(), body.reason());
    }
}
