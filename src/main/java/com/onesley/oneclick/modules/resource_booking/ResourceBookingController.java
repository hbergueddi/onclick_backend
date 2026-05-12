package com.onesley.oneclick.modules.resource_booking;

import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.modules.resource_booking.internal.ResourceBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.*;

@RestController
@RequestMapping("/api/resource-bookings")
@Tag(name = "ResourceBookings", description = "Ressources bookables, tarifs, bookings, invités (§11 — padel/spa/golf/coiffeur/gym)")
public class ResourceBookingController {

    private final ResourceBookingService service;

    public ResourceBookingController(ResourceBookingService service) {
        this.service = service;
    }

    // ─── Resources ───────────────────────────────────────────────────────────

    @GetMapping("/resources")
    @Operation(summary = "Liste paginée de ressources — filtres tenantId / resourceType / enabledOnly")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
    public ResourceDto findResourceById(@PathVariable UUID id) { return service.findResourceById(id); }

    @PostMapping("/resources")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<ResourceDto> createResource(@Valid @RequestBody ResourceCreateDto dto) {
        ResourceDto r = service.createResource(dto);
        return ResponseEntity.created(URI.create("/api/resource-bookings/resources/" + r.id())).body(r);
    }

    @DeleteMapping("/resources/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<Void> deleteResource(@PathVariable UUID id) {
        service.softDeleteResource(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Pricings ────────────────────────────────────────────────────────────

    @GetMapping("/resources/{resourceId}/pricings")
    @PreAuthorize("isAuthenticated()")
    public List<PricingDto> findPricingsByResource(@PathVariable UUID resourceId) {
        return service.findPricingsByResource(resourceId);
    }

    @PostMapping("/pricings")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<PricingDto> createPricing(@Valid @RequestBody PricingCreateDto dto) {
        PricingDto p = service.createPricing(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    // ─── Bookings ────────────────────────────────────────────────────────────

    @GetMapping("/bookings")
    @Operation(summary = "Bookings paginés — filtres resourceId / organizerId / status")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<BookingDto> findAllBookings(
        @RequestParam(required = false) UUID resourceId,
        @RequestParam(required = false) UUID organizerId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllBookings(resourceId, organizerId, status, page, size));
    }

    @GetMapping("/bookings/{id}")
    @PreAuthorize("isAuthenticated()")
    public BookingDto findBookingById(@PathVariable UUID id) { return service.findBookingById(id); }

    @PostMapping("/bookings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BookingDto> createBooking(@Valid @RequestBody BookingCreateDto dto) {
        BookingDto b = service.createBooking(dto);
        return ResponseEntity.created(URI.create("/api/resource-bookings/bookings/" + b.id())).body(b);
    }

    @PatchMapping("/bookings/{id}")
    @PreAuthorize("isAuthenticated()")
    public BookingDto updateBooking(@PathVariable UUID id, @Valid @RequestBody BookingUpdateDto dto) {
        return service.updateBooking(id, dto);
    }

    @DeleteMapping("/bookings/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteBooking(@PathVariable UUID id) {
        service.softDeleteBooking(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Guests ──────────────────────────────────────────────────────────────

    @GetMapping("/bookings/{bookingId}/guests")
    @PreAuthorize("isAuthenticated()")
    public List<GuestDto> findGuestsByBooking(@PathVariable UUID bookingId) {
        return service.findGuestsByBooking(bookingId);
    }

    @PostMapping("/guests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GuestDto> addGuest(@Valid @RequestBody GuestCreateDto dto) {
        GuestDto g = service.addGuest(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(g);
    }
}
