package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.core.identity.internal.User;
import com.onesley.oneclick.core.tenant.internal.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
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
            .map(Resource::toDto);
    }

    public ResourceDto findResourceById(UUID id) {
        return resourceRepo.findById(id)
            .filter(r -> r.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Resource", id))
            .toDto();
    }

    @Transactional
    public ResourceDto createResource(ResourceCreateDto dto) {
        Tenant tenantRef = entityManager.getReference(Tenant.class, dto.tenantId());
        Resource r = new Resource(UUID.randomUUID(), tenantRef, dto.resourceType(), dto.name());
        if (dto.description() != null) r.setDescription(dto.description());
        if (dto.capacity() != null)    r.setCapacity(dto.capacity());
        return resourceRepo.save(r).toDto();
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
        return pricingRepo.findAllByResourceId(resourceId).stream().map(ResourcePricing::toDto).toList();
    }

    @Transactional
    public PricingDto createPricing(PricingCreateDto dto) {
        Resource resourceRef = entityManager.getReference(Resource.class, dto.resourceId());
        ResourcePricing p = new ResourcePricing(UUID.randomUUID(), resourceRef, dto.name(), dto.price());
        if (dto.durationMinutes() != null) p.setDurationMinutes(dto.durationMinutes());
        return pricingRepo.save(p).toDto();
    }

    // ─── Bookings ────────────────────────────────────────────────────────────

    public Page<BookingDto> findAllBookings(UUID resourceId, UUID organizerId, String status, int page, int size) {
        Specification<ResourceBooking> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (resourceId != null)  spec = spec.and((root, q, cb) -> cb.equal(root.get("resourceId"), resourceId));
        if (organizerId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("organizerId"), organizerId));
        if (status != null)      spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return bookingRepo.findAll(spec, PageRequest.of(page, size, Sort.by("startAt").descending()))
            .map(ResourceBooking::toDto);
    }

    public BookingDto findBookingById(UUID id) {
        ResourceBooking b = bookingRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", id));
        SecurityHelper.requireOwnerOrAdmin(b.getOrganizerId());
        return b.toDto();
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
        return bookingRepo.save(b).toDto();
    }

    @Transactional
    public BookingDto updateBooking(UUID id, BookingUpdateDto dto) {
        ResourceBooking b = bookingRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", id));
        SecurityHelper.requireOwnerOrAdmin(b.getOrganizerId());
        if (dto.status() != null) b.setStatus(dto.status());
        if (dto.notes() != null)  b.setNotes(dto.notes());
        return bookingRepo.save(b).toDto();
    }

    @Transactional
    public void softDeleteBooking(UUID id) {
        ResourceBooking b = bookingRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", id));
        SecurityHelper.requireOwnerOrAdmin(b.getOrganizerId());
        b.markDeleted();
        bookingRepo.save(b);
    }

    // ─── Guests ──────────────────────────────────────────────────────────────

    public List<GuestDto> findGuestsByBooking(UUID bookingId) {
        ResourceBooking b = bookingRepo.findById(bookingId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", bookingId));
        SecurityHelper.requireOwnerOrAdmin(b.getOrganizerId());
        return guestRepo.findAllByBookingId(bookingId).stream().map(ResourceBookingGuest::toDto).toList();
    }

    @Transactional
    public GuestDto addGuest(GuestCreateDto dto) {
        ResourceBooking b = bookingRepo.findById(dto.bookingId())
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", dto.bookingId()));
        SecurityHelper.requireOwnerOrAdmin(b.getOrganizerId());
        ResourceBooking bookingRef = entityManager.getReference(ResourceBooking.class, dto.bookingId());
        User guestUserRef = dto.guestUserId() != null
            ? entityManager.getReference(User.class, dto.guestUserId())
            : null;
        ResourceBookingGuest g = new ResourceBookingGuest(UUID.randomUUID(), bookingRef, guestUserRef, dto.guestName());
        return guestRepo.save(g).toDto();
    }
}
