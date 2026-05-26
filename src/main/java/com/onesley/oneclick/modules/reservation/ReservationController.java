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
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import com.onesley.oneclick.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;

/**
 * REST controller {@code /api/reservations} — Bug 32 (Batch A RBAC v2).
 *
 * <p>Tous les endpoints en RBAC v2 senior strict {@code hasAuthority('VERB:RESOURCE')} pendant la phase de transition RBAC v2.
 * Cf. {@link com.onesley.oneclick.security.UserRoleAuthoritiesConverter} pour
 * la résolution des authorities depuis la table {@code permissions}.</p>
 *
 * <p>Le scoping fin (ownership, staff-of-restaurant) reste géré dans
 * {@link ReservationService} via {@code SecurityHelper.requireOwnerOrAdmin}
 * et les checks {@code canAccess()} — RBAC autorise grossièrement, ABAC
 * restreint finement.</p>
 */
@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Workflow réservations (§5)")
@RequiredArgsConstructor
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
    private final RestaurantAccessGuard restaurantAccessGuard;

    public record StatusChangeDto(String status, UUID changedById, String reason) {}

    /**
     * P2 owner-check : accès EN ÉCRITURE à une réservation = client-owner, staff actif
     * du restaurant, ou admin — PAS un simple invité (un guest peut voir via findById
     * mais ne mute pas le statut ni les invités). Le {@link ReservationDto} porte
     * clientId + restaurantId (anti-N+1) ; on s'appuie dessus.
     */
    private void requireReservationWriteAccess(ReservationDto r) {
        if (r.clientId() != null && r.clientId().equals(SecurityHelper.currentUserId())) return;
        if (restaurantAccessGuard.isAdminOrActiveStaffOf(r.restaurantId())) return;
        throw new ForbiddenException(
            "Accès interdit : modification réservée au client, au staff du restaurant ou à un admin");
    }

    @GetMapping
    @Operation(summary = "Liste paginée — filtres clientId / restaurantId / status optionnels")
    @PreAuthorize("hasAuthority('VIEW:RESERVATIONS')")
    public PageResponse<ReservationDto> findAll(
        @RequestParam(required = false) UUID clientId,
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // Anti-fuite dual-ownership : un non-admin ne liste que SES réservations,
        // sauf s'il est staff actif du restaurant demandé (vue ProDesk de SON resto).
        if (!SecurityHelper.isAdmin()
            && (restaurantId == null || !restaurantAccessGuard.isAdminOrActiveStaffOf(restaurantId))) {
            clientId = SecurityHelper.currentUserId();
        }
        return PageResponse.from(service.findAll(clientId, restaurantId, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail réservation par UUID")
    @PreAuthorize("hasAuthority('VIEW:RESERVATIONS')")
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
    @PreAuthorize("hasAuthority('VIEW:RESERVATIONS')")
    public List<ReservationDto> findByIds(@RequestBody List<UUID> ids) {
        return service.findAccessibleByIds(ids);
    }

    @PostMapping
    @Operation(summary = "Crée une réservation (status initial: pending)")
    @PreAuthorize("hasAuthority('CREATE:RESERVATIONS')")
    public ResponseEntity<ReservationDto> create(@Valid @RequestBody ReservationCreateDto dto) {
        ReservationDto r = service.create(dto);
        return ResponseEntity.created(URI.create("/api/reservations/" + r.id())).body(r);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change le statut (workflow audit dans reservation_status_histories)")
    @PreAuthorize("hasAuthority('UPDATE:RESERVATIONS')")
    public ReservationDto changeStatus(@PathVariable UUID id, @RequestBody StatusChangeDto body) {
        // findById enforce l'accès en lecture (404 si absent, 403 si aucun accès) ;
        // on resserre ensuite en écriture (exclut le simple invité).
        requireReservationWriteAccess(service.findById(id));
        return service.changeStatus(id, body.status(), body.changedById(), body.reason());
    }

    @PostMapping("/search")
    @Operation(summary = "Recherche dynamique (Phase 4 §6.3) — 12 opérateurs + whitelist")
    @PreAuthorize("hasAuthority('VIEW:RESERVATIONS')")
    public PageResponse<ReservationDto> search(@RequestBody SearchRequest req) {
        return PageResponse.from(
            Searchable.execute(reservationRepository, req, SEARCHABLE_FIELDS, Reservation::toDto)
        );
    }

    @GetMapping("/count-by-restaurant")
    @Operation(
        summary = "Bug 31 — Compteurs de réservations par restaurant sur une période.",
        description = "KPI brut consommé par la widget admin 'Top Réservations · Par Ville'. " +
                      "Retourne Map<restaurantId, count> triée DESC. Pas de DTO dédié — pattern " +
                      "Map<String,Long> standard du module analytics. Anti-N+1 : 1 SQL groupée."
    )
    @PreAuthorize("hasAuthority('VIEW:RESERVATIONS')")
    public java.util.Map<UUID, Long> countReservationsByRestaurant(
        @RequestParam(defaultValue = "30") int sinceDays,
        @RequestParam(required = false) String status
    ) {
        return service.countReservationsByRestaurant(sinceDays, status);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Booking rules — couverts max, durée slot, fenêtre annulation
    // ═══════════════════════════════════════════════════════════════════════
    // Note d'archi : endpoints exposés sous /api/reservations/ pour rester
    // dans le domaine cohérent reservation, plutôt que /api/restaurants/.
    // Évite un cross-module dependency restaurant → reservation.

    @GetMapping("/booking-rules/by-restaurant/{restaurantId}")
    @Operation(summary = "Liste les règles de réservation d'un restaurant")
    @PreAuthorize("hasAuthority('VIEW:RESERVATIONS')")
    public List<BookingRuleDto> findBookingRulesByRestaurant(@PathVariable UUID restaurantId) {
        return bookingRuleService.findByRestaurant(restaurantId);
    }

    @PostMapping("/booking-rules/by-restaurant/{restaurantId}")
    @Operation(summary = "Crée une règle de réservation pour un restaurant")
    @PreAuthorize("hasAuthority('CREATE:RESERVATIONS')")
    public ResponseEntity<BookingRuleDto> createBookingRule(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody BookingRuleCreateDto dto
    ) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId); // config résa = staff/admin du resto
        BookingRuleDto created = bookingRuleService.create(restaurantId, dto);
        return ResponseEntity.created(URI.create("/api/reservations/booking-rules/" + created.id())).body(created);
    }

    @PatchMapping("/booking-rules/{id}")
    @Operation(summary = "Modifie une règle de réservation")
    @PreAuthorize("hasAuthority('UPDATE:RESERVATIONS')")
    public BookingRuleDto patchBookingRule(
        @PathVariable UUID id,
        @Valid @RequestBody BookingRulePatchDto dto
    ) {
        return bookingRuleService.patch(id, dto);
    }

    @DeleteMapping("/booking-rules/{id}")
    @Operation(summary = "Supprime une règle de réservation")
    @PreAuthorize("hasAuthority('DELETE:RESERVATIONS')")
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
    @PreAuthorize("hasAuthority('VIEW:RESERVATIONS')")
    public List<ReservationGuestDto> findGuestsByReservation(@PathVariable UUID reservationId) {
        service.findById(reservationId); // enforce l'accès en lecture (client/staff/admin/guest) — 403/404 sinon
        return guestService.findByReservation(reservationId);
    }

    @GetMapping("/guests/by-user/{userId}")
    @Operation(summary = "Liste toutes les invitations reçues par un user (Pocket)")
    @PreAuthorize("hasAuthority('VIEW:RESERVATIONS')")
    public List<ReservationGuestDto> findGuestsByUser(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId); // ses propres invitations (ou admin)
        return guestService.findByGuestUser(userId);
    }

    @GetMapping("/guests/by-inviter/{inviterId}")
    @Operation(summary = "Liste toutes les invitations ENVOYÉES par un organisateur (Pocket → « Invitations envoyées »)")
    @PreAuthorize("hasAuthority('VIEW:RESERVATIONS')")
    public List<ReservationGuestDto> findGuestsByInviter(@PathVariable UUID inviterId) {
        SecurityHelper.requireOwnerOrAdmin(inviterId); // ses propres invitations envoyées (ou admin)
        return guestService.findByInviter(inviterId);
    }

    @PostMapping("/{reservationId}/guests")
    @Operation(
        summary = "Invite un guest à une réservation",
        description = "Au moins un identifiant requis : guestUserId, guestPhone, ou guestName"
    )
    @PreAuthorize("hasAuthority('UPDATE:RESERVATIONS')")
    public ResponseEntity<ReservationGuestDto> inviteGuest(
        @PathVariable UUID reservationId,
        @Valid @RequestBody ReservationGuestDto.CreateDto dto
    ) {
        requireReservationWriteAccess(service.findById(reservationId)); // seul owner/staff/admin invite
        ReservationGuestDto created = guestService.invite(reservationId, dto);
        return ResponseEntity.created(URI.create("/api/reservations/guests/" + created.id())).body(created);
    }

    @PatchMapping("/guests/{guestId}/status")
    @Operation(summary = "Change le statut d'une invitation (guest répond OU organisateur annule)")
    @PreAuthorize("hasAuthority('UPDATE:RESERVATIONS')")
    public ReservationGuestDto updateGuestStatus(
        @PathVariable UUID guestId,
        @Valid @RequestBody ReservationGuestDto.StatusUpdateDto dto
    ) {
        return guestService.updateStatus(guestId, dto);
    }

    @PatchMapping("/{reservationId}/guests/mark-seen")
    @Operation(summary = "Marque toutes les réponses des invités comme vues par l'organisateur")
    @PreAuthorize("hasAuthority('UPDATE:RESERVATIONS')")
    public ResponseEntity<Void> markGuestsSeen(@PathVariable UUID reservationId) {
        requireReservationWriteAccess(service.findById(reservationId)); // organisateur/staff/admin
        guestService.markSeen(reservationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/guests/{guestId}")
    @Operation(summary = "Supprime un guest d'une réservation (organisateur)")
    @PreAuthorize("hasAuthority('UPDATE:RESERVATIONS')")
    public ResponseEntity<Void> deleteGuest(@PathVariable UUID guestId) {
        guestService.delete(guestId);
        return ResponseEntity.noContent().build();
    }
}
