package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.search.Searchable;
import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.modules.reservation.api.BookingRuleDtos.BookingRuleCreateDto;
import com.onesley.oneclick.modules.reservation.api.BookingRuleDtos.BookingRuleDto;
import com.onesley.oneclick.modules.reservation.api.BookingRuleDtos.BookingRulePatchDto;
import com.onesley.oneclick.modules.reservation.api.ReservationCreateDto;
import com.onesley.oneclick.modules.reservation.api.ReservationDto;
import com.onesley.oneclick.modules.reservation.api.ReservationGuestDto;
import com.onesley.oneclick.modules.reservation.internal.BookingRuleService;
import com.onesley.oneclick.modules.reservation.internal.Reservation;
import com.onesley.oneclick.modules.reservation.internal.ReservationGuestService;
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
    private final BookingRuleService bookingRuleService;
    private final ReservationGuestService guestService;

    public ReservationController(
        ReservationService service,
        ReservationRepository reservationRepository,
        BookingRuleService bookingRuleService,
        ReservationGuestService guestService
    ) {
        this.service = service;
        this.reservationRepository = reservationRepository;
        this.bookingRuleService = bookingRuleService;
        this.guestService = guestService;
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

    @PostMapping("/batch")
    @Operation(
        summary = "Lookup multiple réservations par UUIDs — anti N+1 (Pocket invitations)",
        description = "Retourne UNIQUEMENT les résas accessibles à l'appelant (client/admin/staff/guest). "
                    + "Les UUIDs sans droit d'accès ou inexistants sont simplement omis du résultat — "
                    + "pas de 403 en cas d'accès partiel."
    )
    @PreAuthorize("isAuthenticated()")
    public List<ReservationDto> findByIds(@RequestBody List<UUID> ids) {
        return service.findAccessibleByIds(ids);
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

    @GetMapping("/top-by-restaurant")
    @Operation(
        summary = "Bug 31 — Top réservations agrégé (restaurant_id, count) sur période et statut.",
        description = "Consommé par la widget admin 'Top Réservations · Par Ville' de la page " +
                      "Restaurants. Le frontend re-agrège par dimension (ville ou nom) à partir " +
                      "de cette liste plate. Anti-N+1 : 1 requête SQL groupée vs fetch-all-then-count."
    )
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public List<com.onesley.oneclick.modules.reservation.api.TopReservationByRestaurantDto>
    topByRestaurant(
        @RequestParam(defaultValue = "30") int sinceDays,
        @RequestParam(required = false) String status
    ) {
        return service.topByRestaurant(sinceDays, status);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Booking rules — couverts max, durée slot, fenêtre annulation
    // ═══════════════════════════════════════════════════════════════════════
    // Note d'archi : endpoints exposés sous /api/reservations/ pour rester
    // dans le domaine cohérent reservation, plutôt que /api/restaurants/.
    // Évite un cross-module dependency restaurant → reservation.

    @GetMapping("/booking-rules/by-restaurant/{restaurantId}")
    @Operation(summary = "Liste les règles de réservation d'un restaurant")
    @PreAuthorize("isAuthenticated()")
    public List<BookingRuleDto> findBookingRulesByRestaurant(@PathVariable UUID restaurantId) {
        return bookingRuleService.findByRestaurant(restaurantId);
    }

    @PostMapping("/booking-rules/by-restaurant/{restaurantId}")
    @Operation(summary = "Crée une règle de réservation pour un restaurant")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<BookingRuleDto> createBookingRule(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody BookingRuleCreateDto dto
    ) {
        BookingRuleDto created = bookingRuleService.create(restaurantId, dto);
        return ResponseEntity.created(URI.create("/api/reservations/booking-rules/" + created.id())).body(created);
    }

    @PatchMapping("/booking-rules/{id}")
    @Operation(summary = "Modifie une règle de réservation")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public BookingRuleDto patchBookingRule(
        @PathVariable UUID id,
        @Valid @RequestBody BookingRulePatchDto dto
    ) {
        return bookingRuleService.patch(id, dto);
    }

    @DeleteMapping("/booking-rules/{id}")
    @Operation(summary = "Supprime une règle de réservation")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<Void> deleteBookingRule(@PathVariable UUID id) {
        bookingRuleService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Reservation guests — workflow invitation (Sprint G.2.1)
    // ═══════════════════════════════════════════════════════════════════════
    // Endpoints sous /api/reservations/ pour rester dans le bounded context
    // reservation (pas de cross-module avec core/identity côté API).

    @GetMapping("/{reservationId}/guests")
    @Operation(summary = "Liste tous les invités d'une réservation")
    @PreAuthorize("isAuthenticated()")
    public List<ReservationGuestDto> findGuestsByReservation(@PathVariable UUID reservationId) {
        return guestService.findByReservation(reservationId);
    }

    @GetMapping("/guests/by-user/{userId}")
    @Operation(summary = "Liste toutes les invitations reçues par un user (Pocket)")
    @PreAuthorize("isAuthenticated()")
    public List<ReservationGuestDto> findGuestsByUser(@PathVariable UUID userId) {
        return guestService.findByGuestUser(userId);
    }

    @PostMapping("/{reservationId}/guests")
    @Operation(
        summary = "Invite un guest à une réservation",
        description = "Au moins un identifiant requis : guestUserId, guestPhone, ou guestName"
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReservationGuestDto> inviteGuest(
        @PathVariable UUID reservationId,
        @Valid @RequestBody ReservationGuestDto.CreateDto dto
    ) {
        ReservationGuestDto created = guestService.invite(reservationId, dto);
        return ResponseEntity.created(URI.create("/api/reservations/guests/" + created.id())).body(created);
    }

    @PatchMapping("/guests/{guestId}/status")
    @Operation(summary = "Change le statut d'une invitation (guest répond OU organisateur annule)")
    @PreAuthorize("isAuthenticated()")
    public ReservationGuestDto updateGuestStatus(
        @PathVariable UUID guestId,
        @Valid @RequestBody ReservationGuestDto.StatusUpdateDto dto
    ) {
        return guestService.updateStatus(guestId, dto);
    }

    @PatchMapping("/{reservationId}/guests/mark-seen")
    @Operation(summary = "Marque toutes les réponses des invités comme vues par l'organisateur")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markGuestsSeen(@PathVariable UUID reservationId) {
        guestService.markSeen(reservationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/guests/{guestId}")
    @Operation(summary = "Supprime un guest d'une réservation (organisateur)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteGuest(@PathVariable UUID guestId) {
        guestService.delete(guestId);
        return ResponseEntity.noContent().build();
    }
}
