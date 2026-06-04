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
import static org.mockito.Mockito.verify;
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
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock EntityManager em;
    /** Horloge fixe → la fenêtre H-2 est déterministe (les bookings de test démarrent à +1h..+2h). */
    private final java.time.Clock fixedClock = java.time.Clock.fixed(Instant.now(), java.time.ZoneOffset.UTC);
    ResourceBookingService service;

    @BeforeEach
    void setup() {
        service = new ResourceBookingService(resourceRepo, pricingRepo, bookingRepo, guestRepo, fixedClock, eventPublisher);
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
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            assertThatThrownBy(() -> service.findBookingById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        ResourceBooking b = booking();
        when(bookingRepo.findById(b.getId())).thenReturn(Optional.of(b));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            // Staff/admin → accès à n'importe quel booking (confirme/consulte).
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            assertThat(service.findBookingById(b.getId())).isNotNull();
        }
    }

    @Test
    void createBooking_fullAndMinimal() {
        // Staff/admin : organizer du DTO respecté (réservation pour le compte d'un membre).
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            assertThat(service.createBooking(new BookingCreateDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), "confirmed", "notes"))).isNotNull();
            assertThat(service.createBooking(new BookingCreateDto(UUID.randomUUID(), UUID.randomUUID(), null,
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), null, null))).isNotNull();
        }
    }

    @Test
    void updateBooking_notFoundAndSuccess() {
        when(bookingRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            assertThatThrownBy(() -> service.updateBooking(UUID.randomUUID(), new BookingUpdateDto("cancelled", "x")))
                .isInstanceOf(NotFoundException.class);
        }
        ResourceBooking b = booking();
        when(bookingRepo.findById(b.getId())).thenReturn(Optional.of(b));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            // Staff/admin confirme/annule n'importe quel booking.
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            service.updateBooking(b.getId(), new BookingUpdateDto("cancelled", "raison"));
            assertThat(b.getStatus()).isEqualTo("cancelled");
        }
    }

    @Test
    void updateBooking_statusChanged_publishesEvent() {
        // Booking 'pending' → 'completed' déclenche un ResourceBookingStatusChangedEvent
        // (consommé par loyalty → auto-punch).
        ResourceBooking b = booking();
        ReflectionTestUtils.setField(b, "status", "pending");
        when(bookingRepo.findById(b.getId())).thenReturn(Optional.of(b));
        org.mockito.ArgumentCaptor<com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent> cap =
            org.mockito.ArgumentCaptor.forClass(
                com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent.class);

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            service.updateBooking(b.getId(), new BookingUpdateDto("completed", null));
        }
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().oldStatus()).isEqualTo("pending");
        assertThat(cap.getValue().newStatus()).isEqualTo("completed");
        assertThat(cap.getValue().bookingId()).isEqualTo(b.getId());
    }

    @Test
    void updateBooking_statusUnchanged_doesNotPublishEvent() {
        // Même statut (ou seulement les notes changent) → AUCUN event publié.
        ResourceBooking b = booking();
        ReflectionTestUtils.setField(b, "status", "confirmed");
        when(bookingRepo.findById(b.getId())).thenReturn(Optional.of(b));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            // status identique + maj notes uniquement
            service.updateBooking(b.getId(), new BookingUpdateDto("confirmed", "juste une note"));
            // status null (non fourni) → pas de changement
            service.updateBooking(b.getId(), new BookingUpdateDto(null, "autre note"));
        }
        org.mockito.Mockito.verifyNoInteractions(eventPublisher);
    }

    @Test
    void softDeleteBooking_notFoundAndSuccess() {
        when(bookingRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            assertThatThrownBy(() -> service.softDeleteBooking(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        ResourceBooking b = booking();
        when(bookingRepo.findById(b.getId())).thenReturn(Optional.of(b));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            // Staff/admin : annulation administrative, non soumise à H-2.
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
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
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
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

    // ─── ABAC self-scope (Lot 0) ─────────────────────────────────────────────────

    @Test
    void createBooking_member_forcesSelfOrganizer_ignoringDtoOrganizer() {
        UUID memberId = UUID.randomUUID();
        UUID spoofedOrganizer = UUID.randomUUID(); // le membre tente de réserver pour un AUTRE
        org.mockito.ArgumentCaptor<UUID> userIdCaptor = org.mockito.ArgumentCaptor.forClass(UUID.class);

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(false); // membre
            sec.when(SecurityHelper::currentUserId).thenReturn(memberId);

            service.createBooking(new BookingCreateDto(UUID.randomUUID(), spoofedOrganizer, null,
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), "pending", null));
        }
        // L'organizer résolu (passé à em.getReference(User.class, ?)) = le membre, PAS le spoof.
        verify(em).getReference(eq(User.class), userIdCaptor.capture());
        assertThat(userIdCaptor.getValue()).isEqualTo(memberId).isNotEqualTo(spoofedOrganizer);
    }

    @Test
    void createBooking_member_noAuth_throwsForbidden() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(null); // pas authentifié
            assertThatThrownBy(() -> service.createBooking(new BookingCreateDto(
                UUID.randomUUID(), UUID.randomUUID(), null,
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), null, null)))
                .isInstanceOf(com.onesley.oneclick.exception.ForbiddenException.class);
        }
    }

    @Test
    void softDeleteBooking_member_withinH2_throwsUnprocessable() {
        // Booking qui démarre dans 1h → < 2h → annulation membre refusée (H-2).
        UUID owner = UUID.randomUUID();
        ResourceBooking soon = new ResourceBooking(UUID.randomUUID(), resource(),
            new User(owner, null, "u@x.ma", "h", "U", "U"),
            Instant.now(fixedClock).plusSeconds(3600), Instant.now(fixedClock).plusSeconds(5400));
        // organizerId est dérivé du FK (insertable=false) → non rempli par le constructeur en test.
        ReflectionTestUtils.setField(soon, "organizerId", owner);
        when(bookingRepo.findById(soon.getId())).thenReturn(Optional.of(soon));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(owner); // owner du booking
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(false); // mais simple membre
            assertThatThrownBy(() -> service.softDeleteBooking(soon.getId()))
                .isInstanceOf(com.onesley.oneclick.exception.UnprocessableException.class);
        }
        assertThat(soon.getDeletedAt()).isNull(); // pas annulé
    }

    @Test
    void softDeleteBooking_member_beyondH2_succeeds() {
        // Booking qui démarre dans 5h → > 2h → annulation membre autorisée.
        UUID owner = UUID.randomUUID();
        ResourceBooking later = new ResourceBooking(UUID.randomUUID(), resource(),
            new User(owner, null, "u@x.ma", "h", "U", "U"),
            Instant.now(fixedClock).plusSeconds(5 * 3600), Instant.now(fixedClock).plusSeconds(6 * 3600));
        ReflectionTestUtils.setField(later, "organizerId", owner);
        when(bookingRepo.findById(later.getId())).thenReturn(Optional.of(later));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(owner);
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(false);
            service.softDeleteBooking(later.getId());
        }
        assertThat(later.getDeletedAt()).isNotNull(); // annulé
    }

    @Test
    void softDeleteBooking_member_notOwner_throwsForbidden() {
        UUID owner = UUID.randomUUID();
        ResourceBooking b = booking();
        ReflectionTestUtils.setField(b, "organizerId", owner);
        when(bookingRepo.findById(b.getId())).thenReturn(Optional.of(b));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID()); // autre user ≠ owner
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(false);
            assertThatThrownBy(() -> service.softDeleteBooking(b.getId()))
                .isInstanceOf(com.onesley.oneclick.exception.ForbiddenException.class);
        }
    }

    // ─── Busy slots (Lot 1) ──────────────────────────────────────────────────────

    @Test
    void findBusySlots_mapsToStartEndOnly_withOccupyingStatuses() {
        ResourceBooking b = booking();
        when(bookingRepo.findActiveInRange(any(), any(), any(), any())).thenReturn(List.of(b));

        UUID resourceId = UUID.randomUUID();
        List<com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BusySlotDto> slots =
            service.findBusySlots(resourceId, java.time.LocalDate.of(2026, 6, 1));

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).startAt()).isEqualTo(b.getStartAt());
        assertThat(slots.get(0).endAt()).isEqualTo(b.getEndAt());

        // Vérifie la fenêtre [00:00, +1j) et les statuts occupants passés au repo.
        org.mockito.ArgumentCaptor<Instant> fromC = org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.ArgumentCaptor<Instant> toC = org.mockito.ArgumentCaptor.forClass(Instant.class);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Collection<String>> statusC =
            org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(bookingRepo).findActiveInRange(eq(resourceId), fromC.capture(), toC.capture(), statusC.capture());
        assertThat(fromC.getValue()).isEqualTo(java.time.LocalDate.of(2026, 6, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        assertThat(toC.getValue()).isEqualTo(java.time.LocalDate.of(2026, 6, 2).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        assertThat(statusC.getValue()).containsExactlyInAnyOrder("pending", "confirmed");
    }
}
