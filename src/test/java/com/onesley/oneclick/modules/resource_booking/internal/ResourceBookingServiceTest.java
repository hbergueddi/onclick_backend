package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingUpdateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.GuestCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.PricingCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceCreateDto;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link ResourceBookingService} (L3 — modules.resource_booking).
 * Resources / Pricings / Bookings / Guests CRUD + soft-delete + RBAC via SecurityHelper mocké.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ResourceBookingServiceTest {

    @Mock ResourceRepository resourceRepo;
    @Mock ResourcePricingRepository pricingRepo;
    @Mock ResourceBookingRepository bookingRepo;
    @Mock ResourceBookingGuestRepository guestRepo;
    @Mock EntityManager em;
    @InjectMocks ResourceBookingService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.getReference(eq(Tenant.class), any())).thenReturn(new Tenant(UUID.randomUUID(), "T", "t"));
        lenient().when(em.getReference(eq(User.class), any())).thenReturn(new User(UUID.randomUUID(), null, "u@x.ma", "h", "U", "U"));
        lenient().when(em.getReference(eq(Resource.class), any())).thenReturn(resource());
        lenient().when(em.getReference(eq(ResourcePricing.class), any())).thenReturn(pricing());
        lenient().when(em.getReference(eq(ResourceBooking.class), any())).thenReturn(booking());
        lenient().when(resourceRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(pricingRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(bookingRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(guestRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Resource resource() {
        return new Resource(UUID.randomUUID(), new Tenant(UUID.randomUUID(), "T", "t"), "padel", "Court 1");
    }
    private ResourcePricing pricing() {
        return new ResourcePricing(UUID.randomUUID(), resource(), "90min", new BigDecimal("200"));
    }
    private ResourceBooking booking() {
        return new ResourceBooking(UUID.randomUUID(), resource(),
            new User(UUID.randomUUID(), null, "u@x.ma", "h", "U", "U"),
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));
    }

    // ─── Resources ─────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void resources_findAll_filtersAndNoFilters() {
        when(resourceRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAllResources(UUID.randomUUID(), "padel", true, 0, 20).getContent()).isEmpty();
        assertThat(service.findAllResources(null, null, false, 0, 20).getContent()).isEmpty();
    }

    @Test
    void findResourceById_notFoundAndFound() {
        when(resourceRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findResourceById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        Resource r = resource();
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        assertThat(service.findResourceById(r.getId())).isNotNull();
    }

    @Test
    void createResource_fullAndMinimal() {
        assertThat(service.createResource(new ResourceCreateDto(UUID.randomUUID(), "padel", "Court 1", "desc", 4))).isNotNull();
        assertThat(service.createResource(new ResourceCreateDto(UUID.randomUUID(), "spa", "Cabine", null, null))).isNotNull();
    }

    @Test
    void softDeleteResource_notFoundAndSuccess() {
        when(resourceRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDeleteResource(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        Resource r = resource();
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        service.softDeleteResource(r.getId());
        assertThat(r.getDeletedAt()).isNotNull();
    }

    // ─── Pricings ──────────────────────────────────────────────────────────────

    @Test
    void pricings_findAndCreate() {
        when(pricingRepo.findAllByResourceId(any())).thenReturn(List.of(pricing()));
        assertThat(service.findPricingsByResource(UUID.randomUUID())).hasSize(1);
        assertThat(service.createPricing(new PricingCreateDto(UUID.randomUUID(), "90min", new BigDecimal("200"), 90))).isNotNull();
        assertThat(service.createPricing(new PricingCreateDto(UUID.randomUUID(), "flat", new BigDecimal("100"), null))).isNotNull();
    }

    // ─── Bookings ──────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void bookings_findAll_filtersAndNoFilters() {
        when(bookingRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAllBookings(UUID.randomUUID(), UUID.randomUUID(), "confirmed", 0, 20).getContent()).isEmpty();
        assertThat(service.findAllBookings(null, null, null, 0, 20).getContent()).isEmpty();
    }

    @Test
    void findBookingById_notFoundAndFound() {
        when(bookingRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.findBookingById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        ResourceBooking b = booking();
        when(bookingRepo.findById(b.getId())).thenReturn(Optional.of(b));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findBookingById(b.getId())).isNotNull();
        }
    }

    @Test
    void createBooking_fullAndMinimal() {
        assertThat(service.createBooking(new BookingCreateDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), "confirmed", "notes"))).isNotNull();
        assertThat(service.createBooking(new BookingCreateDto(UUID.randomUUID(), UUID.randomUUID(), null,
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), null, null))).isNotNull();
    }

    @Test
    void updateBooking_notFoundAndSuccess() {
        when(bookingRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.updateBooking(UUID.randomUUID(), new BookingUpdateDto("cancelled", "x")))
                .isInstanceOf(NotFoundException.class);
        }
        ResourceBooking b = booking();
        when(bookingRepo.findById(b.getId())).thenReturn(Optional.of(b));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.updateBooking(b.getId(), new BookingUpdateDto("cancelled", "raison"));
            assertThat(b.getStatus()).isEqualTo("cancelled");
        }
    }

    @Test
    void softDeleteBooking_notFoundAndSuccess() {
        when(bookingRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.softDeleteBooking(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        ResourceBooking b = booking();
        when(bookingRepo.findById(b.getId())).thenReturn(Optional.of(b));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.softDeleteBooking(b.getId());
            assertThat(b.getDeletedAt()).isNotNull();
        }
    }

    // ─── Guests ────────────────────────────────────────────────────────────────

    @Test
    void guests_findAndAdd() {
        ResourceBooking b = booking();
        when(bookingRepo.findById(any())).thenReturn(Optional.of(b));
        when(guestRepo.findAllByBookingId(any())).thenReturn(List.of(
            new ResourceBookingGuest(UUID.randomUUID(), b, null, "Invité")));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findGuestsByBooking(b.getId())).hasSize(1);
            assertThat(service.addGuest(new GuestCreateDto(b.getId(), UUID.randomUUID(), "Invité"))).isNotNull();
            assertThat(service.addGuest(new GuestCreateDto(b.getId(), null, "Sans compte"))).isNotNull();
        }
    }

    @Test
    void guests_bookingNotFound_throwsNotFound() {
        when(bookingRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.findGuestsByBooking(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> service.addGuest(new GuestCreateDto(UUID.randomUUID(), null, "X"))).isInstanceOf(NotFoundException.class);
        }
    }
}
