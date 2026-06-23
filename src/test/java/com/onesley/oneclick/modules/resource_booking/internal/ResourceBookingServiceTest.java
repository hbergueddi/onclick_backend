package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.exception.UnprocessableException;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingUpdateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.GuestCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.NoShowStatsDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.PricingCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceUpdateDto;
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
    @Mock com.onesley.oneclick.core.identity.api.UserDirectoryApi userDirectory;
    @Mock com.onesley.oneclick.security.TenantScope tenantScope;
    @Mock EntityManager em;
    /** Horloge fixe → la fenêtre H-2 est déterministe (les bookings de test démarrent à +1h..+2h). */
    private final java.time.Clock fixedClock = java.time.Clock.fixed(Instant.now(), java.time.ZoneOffset.UTC);
    ResourceBookingService service;

    @BeforeEach
    void setup() {
        service = new ResourceBookingService(resourceRepo, pricingRepo, bookingRepo, guestRepo, fixedClock, eventPublisher, userDirectory, tenantScope);
        ReflectionTestUtils.setField(service, "entityManager", em);
        // Comportement legacy non scopé par défaut (SUPERADMIN-like) : aucun filtre tenant,
        // toute ressource est visible. Les tests de scoping surchargent ces stubs localement.
        lenient().when(tenantScope.visibleTenantIdsOrNull()).thenReturn(null);
        lenient().when(tenantScope.canSeeTenant(any())).thenReturn(true);
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
        // tenant_id est insertable=false/updatable=false (peuplé par Hibernate depuis la colonne FK) :
        // en test on l'aligne explicitement sur le tenant de l'entité pour que getTenantId() soit cohérent.
        UUID tenantId = UUID.randomUUID();
        Resource r = new Resource(UUID.randomUUID(), new Tenant(tenantId, "T", "t"), "padel", "Court 1");
        ReflectionTestUtils.setField(r, "tenantId", tenantId);
        return r;
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
        // Acteur cross-tenant (SUPERADMIN — VIEW:TENANTS) : le tenantId du DTO est respecté (vue globale).
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(true);
            assertThat(service.createResource(new ResourceCreateDto(UUID.randomUUID(), "padel", "Court 1", "desc", 4, 90, 3, null))).isNotNull();
            assertThat(service.createResource(new ResourceCreateDto(UUID.randomUUID(), "spa", "Cabine", null, null, null, null, null))).isNotNull();
        }
    }

    @Test
    void softDeleteResource_notFoundAndSuccess() {
        when(resourceRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            // Cross-tenant (SUPERADMIN) → gestion du parc autorisée sur n'importe quelle ressource.
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(true);
            assertThatThrownBy(() -> service.softDeleteResource(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
            Resource r = resource();
            when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
            // Aucun booking actif (count = 0 par défaut) → suppression autorisée.
            service.softDeleteResource(r.getId());
            assertThat(r.getDeletedAt()).isNotNull();
        }
    }

    // ─── P1.3 — édition + toggle + 409 delete ────────────────────────────────────

    @Test
    void updateResource_partialPatch_onlyTouchesProvidedFields() {
        // Ressource pré-remplie ; on patche UNIQUEMENT name + slotDurationMinutes.
        Resource r = resource();
        r.setDescription("desc-initiale");
        r.setCapacity(4);
        r.setSlotDurationMinutes(60);
        r.setMaxInvitees(3);
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            // Staff du tenant propriétaire → gestion autorisée (tenant HOME == tenant de la ressource).
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(r.getTenantId()));

            ResourceDto dto = service.updateResource(r.getId(),
                new ResourceUpdateDto("Court Renommé", null, null, 90, null, null));

            // Champs fournis appliqués…
            assertThat(dto.name()).isEqualTo("Court Renommé");
            assertThat(dto.slotDurationMinutes()).isEqualTo(90);
            // …et champs NON fournis inchangés (COALESCE).
            assertThat(dto.description()).isEqualTo("desc-initiale");
            assertThat(dto.capacity()).isEqualTo(4);
            assertThat(dto.maxInvitees()).isEqualTo(3);
            // Le type de ressource n'est jamais touché (verrouillé).
            assertThat(dto.resourceType()).isEqualTo("padel");
        }
    }

    @Test
    void updateResource_notFound_throws() {
        when(resourceRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(true);
            assertThatThrownBy(() -> service.updateResource(UUID.randomUUID(),
                new ResourceUpdateDto("X", null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void updateResource_outOfScope_throwsNotFound() {
        // Ressource existante mais d'un AUTRE tenant que le tenant HOME du staff → 404 (ne divulgue pas l'existence).
        Resource r = resource();
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            // Tenant HOME du staff ≠ tenant de la ressource → hors périmètre de gestion.
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(UUID.randomUUID()));
            assertThatThrownBy(() -> service.updateResource(r.getId(),
                new ResourceUpdateDto("X", null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void updateResource_invalidBusinessValues_throwUnprocessable() {
        Resource r = resource();
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            // Cross-tenant (SUPERADMIN) → gate de gestion passé, on teste les validations métier.
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(true);
            // name blank
            assertThatThrownBy(() -> service.updateResource(r.getId(),
                new ResourceUpdateDto("   ", null, null, null, null, null)))
                .isInstanceOf(UnprocessableException.class);
            // capacity < 1
            assertThatThrownBy(() -> service.updateResource(r.getId(),
                new ResourceUpdateDto(null, null, 0, null, null, null)))
                .isInstanceOf(UnprocessableException.class);
            // slot < 15
            assertThatThrownBy(() -> service.updateResource(r.getId(),
                new ResourceUpdateDto(null, null, null, 10, null, null)))
                .isInstanceOf(UnprocessableException.class);
            // invitees < 0
            assertThatThrownBy(() -> service.updateResource(r.getId(),
                new ResourceUpdateDto(null, null, null, null, -1, null)))
                .isInstanceOf(UnprocessableException.class);
        }
        // aucune sauvegarde sur invalidation
        verify(resourceRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void setResourceEnabled_togglesAndPersists() {
        Resource r = resource();
        r.setEnabled(true);
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(r.getTenantId()));

            ResourceDto off = service.setResourceEnabled(r.getId(), false);
            assertThat(off.enabled()).isFalse();
            assertThat(r.isEnabled()).isFalse();

            ResourceDto on = service.setResourceEnabled(r.getId(), true);
            assertThat(on.enabled()).isTrue();
        }
    }

    @Test
    void setResourceEnabled_outOfScope_throwsNotFound() {
        Resource r = resource();
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(UUID.randomUUID())); // autre tenant
            assertThatThrownBy(() -> service.setResourceEnabled(r.getId(), false))
                .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void createResource_persistsSlotGenerationParams() {
        // Parité création : slotDurationMinutes / maxInvitees / openingHours fournis sont persistés.
        org.mockito.ArgumentCaptor<Resource> cap = org.mockito.ArgumentCaptor.forClass(Resource.class);
        java.util.Map<String, List<String>> hours = java.util.Map.of("mon", List.of("09:00-12:00"));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(true);
            service.createResource(new ResourceCreateDto(
                UUID.randomUUID(), "padel", "Court", "desc", 4, 90, 3, hours));
        }
        verify(resourceRepo).save(cap.capture());
        assertThat(cap.getValue().getSlotDurationMinutes()).isEqualTo(90);
        assertThat(cap.getValue().getMaxInvitees()).isEqualTo(3);
        assertThat(cap.getValue().getOpeningHours()).isEqualTo(hours);
    }

    @Test
    void softDeleteResource_withActiveBookings_throwsConflict() {
        Resource r = resource();
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        when(bookingRepo.countActiveByResourceId(r.getId())).thenReturn(2L);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(true);
            assertThatThrownBy(() -> service.softDeleteResource(r.getId()))
                .isInstanceOf(ConflictException.class);
        }
        // Pas de soft-delete tant que des réservations vivent.
        assertThat(r.getDeletedAt()).isNull();
        verify(resourceRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void softDeleteResource_noActiveBookings_succeeds() {
        Resource r = resource();
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        when(bookingRepo.countActiveByResourceId(r.getId())).thenReturn(0L);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            // Staff du tenant propriétaire → suppression autorisée.
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(r.getTenantId()));
            service.softDeleteResource(r.getId());
        }
        assertThat(r.getDeletedAt()).isNotNull();
    }

    // ─── Pricings ──────────────────────────────────────────────────────────────

    @Test
    void pricings_findAndCreate() {
        // findPricingsByResource gate désormais la ressource via TenantScope → stub findById.
        when(resourceRepo.findById(any())).thenReturn(Optional.of(resource()));
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

    // ─── Board staff (W2-A) : ABAC findTenantBookings ────────────────────────────

    @Test
    void findTenantBookings_clientForbidden() {
        // Un CLIENT (non staff/admin) ne voit JAMAIS les réservations des autres → 403.
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(false);
            assertThatThrownBy(() -> service.findTenantBookings(null, null, 0, 20))
                .isInstanceOf(com.onesley.oneclick.exception.ForbiddenException.class);
        }
    }

    @Test
    void findTenantBookings_staffWithoutTenant_returnsEmpty() {
        // Staff/admin SANS tenant (admin plateforme global) → aucun parc tenant → page vide.
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.empty());
            assertThat(service.findTenantBookings(null, null, 0, 20).getContent()).isEmpty();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void findTenantBookings_staffWithTenant_scopesAndEnriches() {
        // Staff/admin AVEC tenant → scope tenant (Specification sur resources.tenant_id) + enrichissement.
        // Page vide → enrichForStaff no-op (aucun appel namesByIds) → board renvoyé vide, sans erreur.
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(UUID.randomUUID()));
            when(bookingRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
            assertThat(service.findTenantBookings(null, "confirmed", 0, 20).getContent()).isEmpty();
        }
    }

    // ─── P1.4 — no-show stats (agrégation assiduité) ─────────────────────────────

    @Test
    void noShowStats_clientForbidden() {
        // Un CLIENT (non staff/admin) ne voit JAMAIS l'assiduité des autres → 403.
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(false);
            assertThatThrownBy(() -> service.noShowStats(Instant.now().minusSeconds(86400), Instant.now()))
                .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void noShowStats_staffWithoutTenant_returnsEmpty() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.empty());
            assertThat(service.noShowStats(Instant.now().minusSeconds(86400), Instant.now())).isEmpty();
        }
    }

    @Test
    void noShowStats_computesRate_excludesCancelledFromDenominator_andSortsDescAndPassesWindow() {
        UUID tenantId = UUID.randomUUID();
        UUID orgA = UUID.randomUUID();   // 2 honored, 8 no_show, 5 cancelled → taux = 8/(2+8)=80%
        UUID orgB = UUID.randomUUID();   // 4 honored, 1 no_show, 0 cancelled → taux = 1/(4+1)=20%
        UUID orgC = UUID.randomUUID();   // 0 honored, 0 no_show, 3 cancelled → dénominateur 0 → 0%
        Instant lastNoShowA = Instant.parse("2026-05-10T18:00:00Z");

        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to   = Instant.parse("2026-06-01T00:00:00Z");

        List<Object[]> rows = List.of(
            new Object[]{orgA, 15L, 2L, 8L, 5L, lastNoShowA},
            new Object[]{orgB, 5L, 4L, 1L, 0L, Instant.parse("2026-05-03T10:00:00Z")},
            new Object[]{orgC, 3L, 0L, 0L, 3L, null});

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(tenantId));
            when(bookingRepo.aggregateNoShowStatsByOrganizer(eq(tenantId), eq(from), eq(to))).thenReturn(rows);
            when(userDirectory.namesByIds(any())).thenReturn(List.of(
                new UserDirectoryApi.UserName(orgA, "Alice", "A", null, null, null),
                new UserDirectoryApi.UserName(orgB, "Bob", "B", null, null, null),
                new UserDirectoryApi.UserName(orgC, "Carol", "C", null, null, null)));

            List<NoShowStatsDto> stats = service.noShowStats(from, to);

            // Tri no-shows DESC : A (8) > B (1) > C (0).
            assertThat(stats).extracting(NoShowStatsDto::organizerId).containsExactly(orgA, orgB, orgC);

            NoShowStatsDto a = stats.get(0);
            assertThat(a.organizerName()).isEqualTo("Alice A");
            assertThat(a.total()).isEqualTo(15L);
            assertThat(a.honored()).isEqualTo(2L);
            assertThat(a.noShows()).isEqualTo(8L);
            assertThat(a.cancelled()).isEqualTo(5L);
            // Dénominateur = honored + no_show = 10 (les 5 cancelled N'y entrent PAS) → 80%.
            assertThat(a.noShowRatePct()).isEqualTo(80.0);
            assertThat(a.lastNoShowAt()).isEqualTo(lastNoShowA);

            assertThat(stats.get(1).noShowRatePct()).isEqualTo(20.0);
            // Dénominateur 0 (que des annulations) → 0%, lastNoShowAt null.
            assertThat(stats.get(2).noShowRatePct()).isEqualTo(0.0);
            assertThat(stats.get(2).lastNoShowAt()).isNull();
        }
    }

    @Test
    void noShowStats_emptyAggregate_returnsEmpty_noNameLookup() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(UUID.randomUUID()));
            when(bookingRepo.aggregateNoShowStatsByOrganizer(any(), any(), any())).thenReturn(List.of());
            assertThat(service.noShowStats(Instant.now().minusSeconds(86400), Instant.now())).isEmpty();
            verify(userDirectory, org.mockito.Mockito.never()).namesByIds(any());
        }
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
        // findBusySlots gate désormais la ressource via TenantScope → stub findById.
        when(resourceRepo.findById(any())).thenReturn(Optional.of(resource()));
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

    // ─── Périmètre tenant (fuite de périmètre, TenantScope) ──────────────────────

    @Test
    void findAllResources_tenantIdOutOfScope_throwsForbidden() {
        // Un tenantId explicite hors du périmètre visible du caller → 403 (le client ne « devine »
        // jamais les ressources d'un programme PCC/HOMU dont il n'est pas membre).
        UUID foreignTenant = UUID.randomUUID();
        when(tenantScope.canSeeTenant(foreignTenant)).thenReturn(false);
        assertThatThrownBy(() -> service.findAllResources(foreignTenant, null, null, 0, 20))
            .isInstanceOf(com.onesley.oneclick.exception.ForbiddenException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllResources_noTenantId_scopedToVisibleSet() {
        // Sans tenantId : la liste est scopée au périmètre visible (Specification tenantId IN (...)).
        UUID publicTenant = UUID.randomUUID();
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(java.util.Set.of(publicTenant));
        when(resourceRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        assertThat(service.findAllResources(null, null, null, 0, 20).getContent()).isEmpty();
        // La requête est bien partie avec un filtre de périmètre (visibleTenantIdsOrNull consulté).
        verify(tenantScope).visibleTenantIdsOrNull();
        verify(resourceRepo).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllResources_superadmin_bypassesScope() {
        // visibleTenantIdsOrNull() == null (acteur cross-tenant) → AUCUN filtre tenant appliqué.
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(null);
        when(resourceRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAllResources(null, null, null, 0, 20).getContent()).isEmpty();
        verify(tenantScope).visibleTenantIdsOrNull();
    }

    @Test
    void findResourceById_outOfScope_throwsNotFound() {
        // Ressource existante mais hors périmètre → 404 (ne pas divulguer l'existence).
        Resource r = resource();
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        when(tenantScope.canSeeTenant(any())).thenReturn(false);
        assertThatThrownBy(() -> service.findResourceById(r.getId()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findPricingsByResource_outOfScope_throwsNotFound() {
        // Découverte tarifaire gatée par le tenant de la ressource → hors périmètre = 404.
        Resource r = resource();
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        when(tenantScope.canSeeTenant(any())).thenReturn(false);
        assertThatThrownBy(() -> service.findPricingsByResource(r.getId()))
            .isInstanceOf(NotFoundException.class);
        // La grille tarifaire n'est jamais consultée pour une ressource hors périmètre.
        org.mockito.Mockito.verify(pricingRepo, org.mockito.Mockito.never()).findAllByResourceId(any());
    }

    @Test
    void findBusySlots_outOfScope_throwsNotFound() {
        // Disponibilité gatée par le tenant de la ressource → un non-membre ne sonde pas les créneaux.
        Resource r = resource();
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        when(tenantScope.canSeeTenant(any())).thenReturn(false);
        assertThatThrownBy(() -> service.findBusySlots(r.getId(), java.time.LocalDate.of(2026, 6, 1)))
            .isInstanceOf(NotFoundException.class);
        // Aucun créneau n'est consulté pour une ressource hors périmètre.
        org.mockito.Mockito.verify(bookingRepo, org.mockito.Mockito.never())
            .findActiveInRange(any(), any(), any(), any());
    }

    // ─── Scope GESTION STAFF du parc (tenant HOME, calque findTenantBookings) ────────
    // Un owner PCC est STAFF de son tenant (palmeraie), JAMAIS membre → son parc se résout via le
    // tenant HOME (userDirectory.tenantIdById), pas via les memberships (TenantScope). Ces tests
    // verrouillent : (1) création forcée sous le tenant HOME + anti-spoof ; (2) gestion (update/
    // toggle/delete) limitée au tenant HOME ; (3) liste de gestion scopée au tenant HOME.

    /** Ressource rattachée à un tenant DONNÉ (pour vérifier l'alignement staff-tenant). */
    private Resource resourceOfTenant(UUID tenantId) {
        Resource r = new Resource(UUID.randomUUID(), new Tenant(tenantId, "T", "t"), "padel", "Court PCC");
        ReflectionTestUtils.setField(r, "tenantId", tenantId); // tenant_id insertable=false → aligné en test
        return r;
    }

    @Test
    void createResource_staff_forcesHomeTenant_ignoringDtoTenant() {
        // Owner PCC (staff, NON cross-tenant) : la ressource est créée sous SON tenant HOME, même si
        // le DTO porte le MÊME tenant. On capture le Tenant.class référencé pour le vérifier.
        UUID homeTenant = UUID.randomUUID();
        org.mockito.ArgumentCaptor<UUID> tenantRefCaptor = org.mockito.ArgumentCaptor.forClass(UUID.class);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(false); // pas SUPERADMIN
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(homeTenant));

            // DTO sans tenantId explicite (le natif peut envoyer un tenantId imparfait — corrigé serveur).
            assertThat(service.createResource(new ResourceCreateDto(
                homeTenant, "padel", "Court", null, 4, 60, 3, null))).isNotNull();
        }
        // em.getReference(Tenant.class, ?) est bien appelé avec le tenant HOME du staff.
        verify(em, org.mockito.Mockito.atLeastOnce()).getReference(eq(Tenant.class), tenantRefCaptor.capture());
        assertThat(tenantRefCaptor.getValue()).isEqualTo(homeTenant);
    }

    @Test
    void createResource_staff_spoofsForeignTenant_throwsForbidden() {
        // Owner PCC qui tente de créer une ressource dans un AUTRE tenant que le sien → 403 (anti-spoof).
        UUID homeTenant = UUID.randomUUID();
        UUID foreignTenant = UUID.randomUUID();
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(homeTenant));

            assertThatThrownBy(() -> service.createResource(new ResourceCreateDto(
                foreignTenant, "padel", "Court", null, 4, 60, 3, null)))
                .isInstanceOf(ForbiddenException.class);
        }
        // Anti-spoof : aucune ressource persistée.
        verify(resourceRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createResource_staffWithoutHomeTenant_throwsForbidden() {
        // Staff/admin sans tenant HOME (admin plateforme global non SUPERADMIN) → 403 (pas de rattachement).
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("VIEW:TENANTS")).thenReturn(false);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createResource(new ResourceCreateDto(
                UUID.randomUUID(), "padel", "Court", null, 4, 60, 3, null)))
                .isInstanceOf(ForbiddenException.class);
        }
        verify(resourceRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateResource_staff_sameHomeTenant_succeeds() {
        // Owner PCC édite une ressource de SON tenant → 200 (patch appliqué).
        UUID homeTenant = UUID.randomUUID();
        Resource r = resourceOfTenant(homeTenant);
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(homeTenant));

            ResourceDto dto = service.updateResource(r.getId(),
                new ResourceUpdateDto("Court Renommé", null, 8, null, null, null));
            assertThat(dto.name()).isEqualTo("Court Renommé");
            assertThat(dto.capacity()).isEqualTo(8);
        }
    }

    @Test
    void setResourceEnabled_staff_foreignTenant_throwsNotFound() {
        // Owner PCC tente de toggler une ressource d'un AUTRE tenant → 404 (ne divulgue pas l'existence).
        Resource r = resourceOfTenant(UUID.randomUUID());
        when(resourceRepo.findById(r.getId())).thenReturn(Optional.of(r));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(UUID.randomUUID())); // autre tenant
            assertThatThrownBy(() -> service.setResourceEnabled(r.getId(), false))
                .isInstanceOf(NotFoundException.class);
        }
        verify(resourceRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllResources_staffManager_noTenantId_scopesToHomeTenant() {
        // Owner PCC ouvre son écran de gestion (aucun tenantId explicite) : la liste est scopée à SON
        // tenant HOME (via tenantIdById), PAS au périmètre membership-visible (qui exclurait palmeraie).
        UUID homeTenant = UUID.randomUUID();
        when(resourceRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("CREATE:RESOURCE_BOOKINGS")).thenReturn(true);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(homeTenant));

            assertThat(service.findAllResources(null, null, null, 0, 20).getContent()).isEmpty();

            // Scope HOME tenant emprunté → tenantIdById consulté, périmètre membership PAS consulté.
            verify(userDirectory).tenantIdById(any());
            verify(tenantScope, org.mockito.Mockito.never()).visibleTenantIdsOrNull();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllResources_staffManager_ownHomeTenantExplicit_allowed() {
        // tenantId explicite == tenant HOME du staff → autorisé même si hors périmètre membership
        // (canSeeTenant=false), car c'est SON parc.
        UUID homeTenant = UUID.randomUUID();
        when(tenantScope.canSeeTenant(homeTenant)).thenReturn(false); // pas membre, mais staff de ce tenant
        when(resourceRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.hasAuthority("UPDATE:RESOURCE_BOOKINGS")).thenReturn(true);
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            when(userDirectory.tenantIdById(any())).thenReturn(Optional.of(homeTenant));

            assertThat(service.findAllResources(homeTenant, null, null, 0, 20).getContent()).isEmpty();
        }
    }
}
