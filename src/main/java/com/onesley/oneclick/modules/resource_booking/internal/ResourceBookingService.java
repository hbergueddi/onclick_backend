package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.core.identity.internal.User;
import com.onesley.oneclick.core.tenant.internal.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.*;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingUpdateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.GuestCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.GuestDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.PricingCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.PricingDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceDto;

@Service
@Transactional(readOnly = true)
public class ResourceBookingService {

    private final ResourceRepository resourceRepo;
    private final ResourcePricingRepository pricingRepo;
    private final ResourceBookingRepository bookingRepo;
    private final ResourceBookingGuestRepository guestRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public ResourceBookingService(ResourceRepository resourceRepo,
                                  ResourcePricingRepository pricingRepo,
                                  ResourceBookingRepository bookingRepo,
                                  ResourceBookingGuestRepository guestRepo) {
        this.resourceRepo = resourceRepo;
        this.pricingRepo = pricingRepo;
        this.bookingRepo = bookingRepo;
        this.guestRepo = guestRepo;
    }

    // ─── Resources ───────────────────────────────────────────────────────────

    public Page<ResourceDto> findAllResources(UUID tenantId, String resourceType, Boolean enabledOnly, int page, int size) {
        Specification<Resource> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (tenantId != null)     spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        if (resourceType != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("resourceType"), resourceType));
        if (Boolean.TRUE.equals(enabledOnly)) {
            spec = spec.and((root, q, cb) -> cb.isTrue(root.get("enabled")));
        }
        return resourceRepo.findAll(spec, PageRequest.of(page, size, Sort.by("name").ascending()))
            .map(ResourceDto::from);
    }

    public ResourceDto findResourceById(UUID id) {
        return ResourceDto.from(resourceRepo.findById(id)
            .filter(r -> r.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Resource", id)));
    }

    @Transactional
    public ResourceDto createResource(ResourceCreateDto dto) {
        Tenant tenantRef = entityManager.getReference(Tenant.class, dto.tenantId());
        Resource r = new Resource(UUID.randomUUID(), tenantRef, dto.resourceType(), dto.name());
        if (dto.description() != null) r.setDescription(dto.description());
        if (dto.capacity() != null)    r.setCapacity(dto.capacity());
        return ResourceDto.from(resourceRepo.save(r));
    }

    @Transactional
    public void softDeleteResource(UUID id) {
        Resource r = resourceRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Resource", id));
        r.markDeleted();
        resourceRepo.save(r);
    }

    // ─── Pricings ────────────────────────────────────────────────────────────

    public List<PricingDto> findPricingsByResource(UUID resourceId) {
        return pricingRepo.findAllByResourceId(resourceId).stream().map(PricingDto::from).toList();
    }

    @Transactional
    public PricingDto createPricing(PricingCreateDto dto) {
        Resource resourceRef = entityManager.getReference(Resource.class, dto.resourceId());
        ResourcePricing p = new ResourcePricing(UUID.randomUUID(), resourceRef, dto.name(), dto.price());
        if (dto.durationMinutes() != null) p.setDurationMinutes(dto.durationMinutes());
        return PricingDto.from(pricingRepo.save(p));
    }

    // ─── Bookings ────────────────────────────────────────────────────────────

    public Page<BookingDto> findAllBookings(UUID resourceId, UUID organizerId, String status, int page, int size) {
        Specification<ResourceBooking> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (resourceId != null)  spec = spec.and((root, q, cb) -> cb.equal(root.get("resourceId"), resourceId));
        if (organizerId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("organizerId"), organizerId));
        if (status != null)      spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return bookingRepo.findAll(spec, PageRequest.of(page, size, Sort.by("startAt").descending()))
            .map(BookingDto::from);
    }

    public BookingDto findBookingById(UUID id) {
        return BookingDto.from(bookingRepo.findById(id)
            .filter(b -> b.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", id)));
    }

    @Transactional
    public BookingDto createBooking(BookingCreateDto dto) {
        Resource resourceRef = entityManager.getReference(Resource.class, dto.resourceId());
        User organizerRef = entityManager.getReference(User.class, dto.organizerId());
        ResourceBooking b = new ResourceBooking(UUID.randomUUID(), resourceRef, organizerRef, dto.startAt(), dto.endAt());
        if (dto.pricingId() != null) {
            b.setPricing(entityManager.getReference(ResourcePricing.class, dto.pricingId()));
        }
        if (dto.status() != null) b.setStatus(dto.status());
        if (dto.notes() != null)  b.setNotes(dto.notes());
        return BookingDto.from(bookingRepo.save(b));
    }

    @Transactional
    public BookingDto updateBooking(UUID id, BookingUpdateDto dto) {
        ResourceBooking b = bookingRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", id));
        if (dto.status() != null) b.setStatus(dto.status());
        if (dto.notes() != null)  b.setNotes(dto.notes());
        return BookingDto.from(bookingRepo.save(b));
    }

    @Transactional
    public void softDeleteBooking(UUID id) {
        ResourceBooking b = bookingRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", id));
        b.markDeleted();
        bookingRepo.save(b);
    }

    // ─── Guests ──────────────────────────────────────────────────────────────

    public List<GuestDto> findGuestsByBooking(UUID bookingId) {
        return guestRepo.findAllByBookingId(bookingId).stream().map(GuestDto::from).toList();
    }

    @Transactional
    public GuestDto addGuest(GuestCreateDto dto) {
        ResourceBooking bookingRef = entityManager.getReference(ResourceBooking.class, dto.bookingId());
        User guestUserRef = dto.guestUserId() != null
            ? entityManager.getReference(User.class, dto.guestUserId())
            : null;
        ResourceBookingGuest g = new ResourceBookingGuest(UUID.randomUUID(), bookingRef, guestUserRef, dto.guestName());
        return GuestDto.from(guestRepo.save(g));
    }
}
