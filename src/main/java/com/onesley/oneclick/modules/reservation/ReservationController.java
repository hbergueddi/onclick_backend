package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.search.Searchable;
import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.modules.reservation.api.ReservationCreateDto;
import com.onesley.oneclick.modules.reservation.api.ReservationDto;
import com.onesley.oneclick.modules.reservation.internal.Reservation;
import com.onesley.oneclick.modules.reservation.internal.ReservationRepository;
import com.onesley.oneclick.modules.reservation.internal.ReservationService;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Workflow réservations (§5)")
public class ReservationController {

    /** Whitelist Phase 4 §6.3 — champs filtrables/sortables. */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "tenantId", "clientId", "restaurantId", "tableId", "serviceId",
        "reservationAt", "guestCount", "status", "createdAt", "updatedAt"
    );

    private final ReservationService service;
    private final ReservationRepository reservationRepository;

    public ReservationController(ReservationService service, ReservationRepository reservationRepository) {
        this.service = service;
        this.reservationRepository = reservationRepository;
    }

    public record StatusChangeDto(String status, UUID changedById, String reason) {}

    @GetMapping
    @Operation(summary = "Liste paginée — filtres clientId / restaurantId / status optionnels")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
    public ReservationDto findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Crée une réservation (status initial: pending)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReservationDto> create(@Valid @RequestBody ReservationCreateDto dto) {
        ReservationDto r = service.create(dto);
        return ResponseEntity.created(URI.create("/api/reservations/" + r.id())).body(r);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change le statut (workflow audit dans reservation_status_histories)")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ReservationDto changeStatus(@PathVariable UUID id, @RequestBody StatusChangeDto body) {
        return service.changeStatus(id, body.status(), body.changedById(), body.reason());
    }

    @PostMapping("/search")
    @Operation(summary = "Recherche dynamique (Phase 4 §6.3) — 12 opérateurs + whitelist")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ReservationDto> search(@RequestBody SearchRequest req) {
        return PageResponse.from(
            Searchable.execute(reservationRepository, req, SEARCHABLE_FIELDS, Reservation::toDto)
        );
    }
}
