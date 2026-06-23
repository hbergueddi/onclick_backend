package com.onesley.oneclick.modules.resource_booking;

import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.modules.resource_booking.internal.ResourceBookingService;
import com.onesley.oneclick.modules.resource_booking.internal.ResourceBookingDashboardPublisher;
import com.onesley.oneclick.security.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import static com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.*;
import lombok.RequiredArgsConstructor;

/**
 * RBAC v2 senior strict — {@code @PreAuthorize("hasAuthority('VERB:RESOURCE')")} uniquement.
 *
 * <p><b>Split d'autorité (PCC Lot 0, migration V66)</b> — deux ressources distinctes :
 * <ul>
 *   <li><b>RESOURCE_BOOKINGS</b> = gestion du <b>parc</b> (ressources + tarifs) :
 *       {@code /resources} (GET/POST/DELETE) + {@code /pricings} (POST). Réservé admin/staff
 *       qui détiennent {VERB}:RESOURCE_BOOKINGS. Le CLIENT ne garde que VIEW:RESOURCE_BOOKINGS
 *       pour <i>lister</i> les ressources réservables.</li>
 *   <li><b>BOOKINGS</b> = workflow <b>réservation membre</b> : {@code /bookings} (GET/POST/PATCH/
 *       DELETE) + {@code /guests} (POST) + {@code /busy-slots} (GET). Le CLIENT détient
 *       {VERB}:BOOKINGS et gère SES bookings (self-scope ABAC forcé côté service) ; le staff/admin
 *       confirme/annule/marque.</li>
 * </ul>
 * Séparer ces deux autorités empêche l'escalade : donner à un membre le droit de réserver
 * (CREATE:BOOKINGS) ne lui donne PAS le droit de créer des ressources (CREATE:RESOURCE_BOOKINGS).
 */
@RestController
@RequestMapping("/api/resource-bookings")
@Tag(name = "ResourceBookings", description = "Ressources bookables, tarifs, bookings, invités (§11 — padel/spa/golf/coiffeur/gym)")
@RequiredArgsConstructor
public class ResourceBookingController {

    private final ResourceBookingService service;

    /**
     * Publisher temps réel du board staff (STOMP {@code /topic/resource-bookings}). Appelé
     * APRÈS commit (depuis le controller, comme {@code DisputeController}) à chaque mutation
     * de booking → latence dashboard minimale, 0 polling. Push best-effort (cf {@link #safePush()}).
     */
    private final ResourceBookingDashboardPublisher dashboardPublisher;

    // ─── Resources ───────────────────────────────────────────────────────────

    @GetMapping("/resources")
    @Operation(summary = "Liste paginée de ressources — filtres tenantId / resourceType / enabledOnly")
    @PreAuthorize("hasAuthority('VIEW:RESOURCE_BOOKINGS')")
    public PageResponse<ResourceDto> findAllResources(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) String resourceType,
        @RequestParam(required = false) Boolean enabledOnly,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllResources(tenantId, resourceType, enabledOnly, page, size));
    }

    @GetMapping("/resources/{id}")
    @PreAuthorize("hasAuthority('VIEW:RESOURCE_BOOKINGS')")
    public ResourceDto findResourceById(@PathVariable UUID id) { return service.findResourceById(id); }

    @PostMapping("/resources")
    @PreAuthorize("hasAuthority('CREATE:RESOURCE_BOOKINGS')")
    public ResponseEntity<ResourceDto> createResource(@Valid @RequestBody ResourceCreateDto dto) {
        ResourceDto r = service.createResource(dto);
        return ResponseEntity.created(URI.create("/api/resource-bookings/resources/" + r.id())).body(r);
    }

    @PatchMapping("/resources/{id}")
    @Operation(summary = "P1.3 — édition partielle d'une ressource (COALESCE ; type verrouillé)")
    @PreAuthorize("hasAuthority('UPDATE:RESOURCE_BOOKINGS')")
    public ResourceDto updateResource(@PathVariable UUID id, @Valid @RequestBody ResourceUpdateDto dto) {
        return service.updateResource(id, dto);
    }

    @PatchMapping("/resources/{id}/enabled")
    @Operation(summary = "P1.3 — active/désactive une ressource (toggle parc)")
    @PreAuthorize("hasAuthority('UPDATE:RESOURCE_BOOKINGS')")
    public ResourceDto setResourceEnabled(@PathVariable UUID id, @Valid @RequestBody ResourceEnabledPatchDto dto) {
        return service.setResourceEnabled(id, dto.enabled());
    }

    @DeleteMapping("/resources/{id}")
    @PreAuthorize("hasAuthority('DELETE:RESOURCE_BOOKINGS')")
    public ResponseEntity<Void> deleteResource(@PathVariable UUID id) {
        service.softDeleteResource(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Pricings ────────────────────────────────────────────────────────────

    @GetMapping("/resources/{resourceId}/pricings")
    @PreAuthorize("hasAuthority('VIEW:RESOURCE_BOOKINGS')")
    public List<PricingDto> findPricingsByResource(@PathVariable UUID resourceId) {
        return service.findPricingsByResource(resourceId);
    }

    @PostMapping("/pricings")
    @PreAuthorize("hasAuthority('CREATE:RESOURCE_BOOKINGS')")
    public ResponseEntity<PricingDto> createPricing(@Valid @RequestBody PricingCreateDto dto) {
        PricingDto p = service.createPricing(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    // ─── Disponibilité (calendrier) ────────────────────────────────────────────

    /**
     * Créneaux occupés d'une ressource un jour donné — SANS aucune PII.
     *
     * <p>Le calendrier de réservation membre a besoin de savoir QUELS créneaux sont pris,
     * mais PAS QUI a réservé. On expose donc uniquement {@code (startAt, endAt)} (aucun
     * organizer / invité). Garde {@code VIEW:BOOKINGS} : tout membre PCC peut consulter la
     * disponibilité pour choisir un créneau libre.</p>
     */
    @GetMapping("/resources/{resourceId}/busy-slots")
    @Operation(summary = "Créneaux occupés d'une ressource un jour donné (sans PII : start/end uniquement)")
    @PreAuthorize("hasAuthority('VIEW:BOOKINGS')")
    public List<BusySlotDto> findBusySlots(
        @PathVariable UUID resourceId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.findBusySlots(resourceId, date);
    }

    // ─── Bookings ────────────────────────────────────────────────────────────

    @GetMapping("/bookings")
    @Operation(summary = "Bookings paginés — filtres resourceId / organizerId / status")
    @PreAuthorize("hasAuthority('VIEW:BOOKINGS')")
    public PageResponse<BookingDto> findAllBookings(
        @RequestParam(required = false) UUID resourceId,
        @RequestParam(required = false) UUID organizerId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // Anti-fuite : un non-admin ne liste QUE ses propres bookings (cohérent avec
        // bookings/{id} déjà gardé owner+admin côté service). NB : si la gestion PCC
        // staff par-ressource est requise, l'élargir via requireAdminOrActiveStaffOf.
        if (!SecurityHelper.isAdmin()) {
            organizerId = SecurityHelper.currentUserId();
        }
        return PageResponse.from(service.findAllBookings(resourceId, organizerId, status, page, size));
    }

    @GetMapping("/bookings/tenant")
    @Operation(summary = "Board staff : toutes les réservations du tenant (nom orga/ressource) — live /topic/resource-bookings")
    @PreAuthorize("hasAuthority('VIEW:BOOKINGS')")
    public PageResponse<StaffBookingDto> findTenantBookings(
        @RequestParam(required = false) UUID resourceId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // ABAC service : staff/admin uniquement (un CLIENT → 403) + scope tenant du caller
        // (anti-spoof : le tenant n'est jamais un paramètre client, il vient du JWT).
        return PageResponse.from(service.findTenantBookings(resourceId, status, page, size));
    }

    @GetMapping("/no-show-stats")
    @Operation(summary = "P1.4 — assiduité par organisateur (no-show stats) du tenant sur [from, to)")
    @PreAuthorize("hasAuthority('VIEW:BOOKINGS')")
    public List<NoShowStatsDto> noShowStats(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        // Scope tenant + ABAC staff/admin résolus côté service (calque exact de findTenantBookings) :
        // le tenant vient du JWT, jamais d'un paramètre client (anti-spoof) ; un CLIENT → 403.
        return service.noShowStats(from, to);
    }

    /** Push STOMP best-effort du board staff (un échec de push ne casse jamais la mutation). */
    private void safePush() {
        try { dashboardPublisher.pushNow(); } catch (RuntimeException ignored) { /* best-effort */ }
    }

    @GetMapping("/bookings/{id}")
    @PreAuthorize("hasAuthority('VIEW:BOOKINGS')")
    public BookingDto findBookingById(@PathVariable UUID id) { return service.findBookingById(id); }

    @PostMapping("/bookings")
    @PreAuthorize("hasAuthority('CREATE:BOOKINGS')")
    public ResponseEntity<BookingDto> createBooking(@Valid @RequestBody BookingCreateDto dto) {
        BookingDto b = service.createBooking(dto);
        safePush();
        return ResponseEntity.created(URI.create("/api/resource-bookings/bookings/" + b.id())).body(b);
    }

    @PatchMapping("/bookings/{id}")
    @PreAuthorize("hasAuthority('UPDATE:BOOKINGS')")
    public BookingDto updateBooking(@PathVariable UUID id, @Valid @RequestBody BookingUpdateDto dto) {
        BookingDto r = service.updateBooking(id, dto);
        safePush();
        return r;
    }

    @DeleteMapping("/bookings/{id}")
    @PreAuthorize("hasAuthority('DELETE:BOOKINGS')")
    public ResponseEntity<Void> deleteBooking(@PathVariable UUID id) {
        service.softDeleteBooking(id);
        safePush();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Guests ──────────────────────────────────────────────────────────────

    @GetMapping("/bookings/{bookingId}/guests")
    @PreAuthorize("hasAuthority('VIEW:BOOKINGS')")
    public List<GuestDto> findGuestsByBooking(@PathVariable UUID bookingId) {
        return service.findGuestsByBooking(bookingId);
    }

    @PostMapping("/guests")
    @PreAuthorize("hasAuthority('UPDATE:BOOKINGS')")
    public ResponseEntity<GuestDto> addGuest(@Valid @RequestBody GuestCreateDto dto) {
        GuestDto g = service.addGuest(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(g);
    }
}
