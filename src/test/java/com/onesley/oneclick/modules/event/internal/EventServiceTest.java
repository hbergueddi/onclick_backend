package com.onesley.oneclick.modules.event.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.event.api.Event;
import com.onesley.oneclick.modules.event.api.EventDtos.EventCreateDto;
import com.onesley.oneclick.modules.event.api.EventDtos.EventPatchDto;
import com.onesley.oneclick.modules.event.api.EventDtos.ParticipationCreateDto;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
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

import com.onesley.oneclick.exception.ForbiddenException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link EventService} (L3 — modules.event).
 * Events CRUD + RSVP (capacité, doublon, increment/decrement places).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceTest {

    @Mock EventRepository eventRepo;
    @Mock EventParticipationRepository participationRepo;
    @Mock EntityManager entityManager;
    @Mock com.onesley.oneclick.security.TenantScope tenantScope;
    @InjectMocks EventService service;

    /**
     * ABAC self-scope (rsvp/cancelRsvp) lit {@link SecurityHelper} en statique.
     * On simule un appelant staff/admin pour TOUTE la classe : {@code rsvp} utilise
     * alors {@code dto.userId()} (= {@link #userId}) — ce qui matche les stubs
     * {@code findByEventIdAndUserId(eventId, userId)} — et {@code cancelRsvp} ne lève
     * pas Forbidden. Le scénario membre/anti-spoof est couvert en L4 (intégration).
     */
    private MockedStatic<SecurityHelper> securityMock;

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        securityMock = mockStatic(SecurityHelper.class);
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        securityMock.when(SecurityHelper::currentUserId).thenReturn(userId);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        lenient().when(entityManager.getReference(eq(Tenant.class), any())).thenReturn(new Tenant(UUID.randomUUID(), "T", "t"));
        lenient().when(entityManager.getReference(eq(Event.class), any())).thenReturn(event(null));
        lenient().when(entityManager.getReference(eq(User.class), any())).thenReturn(new User(UUID.randomUUID(), null, "u@x.ma", "h", "U", "U"));
        lenient().when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(participationRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        // Défaut tests : aucun filtre tenant (équiv. SUPERADMIN) → comportement legacy non scopé.
        lenient().when(tenantScope.visibleTenantIdsOrNull()).thenReturn(null);
        lenient().when(tenantScope.canSeeTenant(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) securityMock.close();
    }

    private Event event(Integer capacity) {
        Event e = new Event(eventId, new Tenant(UUID.randomUUID(), "T", "t"), "Soirée", Instant.now().plusSeconds(86400));
        if (capacity != null) e.setCapacity(capacity);
        return e;
    }
    private EventParticipation participation(String status) {
        return new EventParticipation(UUID.randomUUID(), event(null),
            new User(userId, null, "u@x.ma", "h", "U", "U"), status);
    }

    // ─── events CRUD ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void findAll_delegates_withFilters() {
        when(eventRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAll(UUID.randomUUID(), UUID.randomUUID(), true, 0, 20).getContent()).isEmpty();
    }

    // ─── Périmètre tenant (fuite de périmètre) ───────────────────────────────────

    @Test
    void findAll_tenantIdOutOfScope_throwsForbidden() {
        // Le client passe un tenantId d'un programme dont il n'est pas membre → 403 (pas d'exposition).
        when(tenantScope.canSeeTenant(any())).thenReturn(false);
        assertThatThrownBy(() -> service.findAll(UUID.randomUUID(), null, null, 0, 20))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_noTenantId_scopedByVisibleSet() {
        // Sans tenantId : la requête est scopée au périmètre visible (set non-null) → délègue au repo.
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(Set.of(UUID.randomUUID()));
        when(eventRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAll(null, null, null, 0, 20).getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_superadminBypass_noTenantFilter() {
        // SUPERADMIN : visibleTenantIdsOrNull()==null → aucun filtre tenant, délègue.
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(null);
        when(eventRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAll(null, null, null, 0, 20).getContent()).isEmpty();
    }

    @Test
    void findById_notFoundAndFound() {
        when(eventRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event(null)));
        assertThat(service.findById(eventId)).isNotNull();
    }

    @Test
    void findById_outOfScope_throwsNotFound() {
        // Event d'un tenant hors périmètre → 404 (pas de divulgation d'existence).
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event(null)));
        when(tenantScope.canSeeTenant(any())).thenReturn(false);
        assertThatThrownBy(() -> service.findById(eventId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findParticipations_outOfScope_throwsNotFound() {
        // La liste des inscrits (PII) d'un event hors périmètre ne doit pas fuiter → 404.
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event(null)));
        when(tenantScope.canSeeTenant(any())).thenReturn(false);
        assertThatThrownBy(() -> service.findParticipations(eventId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_full_andMinimal() {
        assertThat(service.create(new EventCreateDto(UUID.randomUUID(), UUID.randomUUID(), "Gala", "desc",
            "elite", Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), 100,
            "Ruby", "http://img", "Salle A", true))).isNotNull();
        assertThat(service.create(new EventCreateDto(UUID.randomUUID(), null, "Simple", null,
            null, Instant.now().plusSeconds(3600), null, null, null, null, null, null))).isNotNull();
    }

    @Test
    void patch_notFound_andSuccess() {
        when(eventRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(),
            new EventPatchDto("X", null, null, null, null, null, null, null, null, null))).isInstanceOf(NotFoundException.class);
        Event e = event(null);
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(e));
        service.patch(eventId, new EventPatchDto("Renommé", "d", "elite", Instant.now(), null, 50, "Ruby", "i", "loc", false));
        assertThat(e.getTitle()).isEqualTo("Renommé");
    }

    @Test
    void softDelete_notFound_andSuccess() {
        when(eventRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDelete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        Event e = event(null);
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(e));
        service.softDelete(eventId);
        assertThat(e.getDeletedAt()).isNotNull();
    }

    // ─── participations / RSVP ──────────────────────────────────────────────────

    @Test
    void findParticipations_byEvent_andByUser_map() {
        // findParticipations charge l'event pour le contrôle de périmètre (canSeeTenant) avant la liste.
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event(null)));
        when(participationRepo.findAllByEventIdFetchUser(eventId)).thenReturn(List.of(participation("going")));
        when(participationRepo.findAllByUserIdFetchUser(userId)).thenReturn(List.of(participation("maybe")));
        var byEvent = service.findParticipations(eventId);
        assertThat(byEvent).hasSize(1);
        // identité membre exposée (jointure user) — affichée dans la liste des inscrits
        assertThat(byEvent.get(0).memberFirstName()).isEqualTo("U");
        assertThat(byEvent.get(0).memberEmail()).isEqualTo("u@x.ma");
        assertThat(service.findParticipationsByUser(userId)).hasSize(1);
    }

    @Test
    void rsvp_eventNotFound_throwsNotFound() {
        when(eventRepo.findById(eventId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rsvp(new ParticipationCreateDto(eventId, userId, "going", null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rsvp_duplicate_throwsConflict() {
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event(null)));
        when(participationRepo.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.of(participation("going")));
        assertThatThrownBy(() -> service.rsvp(new ParticipationCreateDto(eventId, userId, "going", null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void rsvp_full_throwsBadRequest() {
        Event full = event(0); // capacity 0 -> hasCapacity false
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(full));
        when(participationRepo.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rsvp(new ParticipationCreateDto(eventId, userId, "going", null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rsvp_going_incrementsPlaces() {
        Event e = event(10);
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(e));
        when(participationRepo.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.empty());
        service.rsvp(new ParticipationCreateDto(eventId, userId, "going", "Mon +1"));
        verify(eventRepo).save(e); // increment places persisté
    }

    @Test
    void rsvp_notGoing_noIncrement() {
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event(10)));
        when(participationRepo.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.empty());
        service.rsvp(new ParticipationCreateDto(eventId, userId, "maybe", null));
        verify(eventRepo, never()).save(any()); // pas de going -> pas d'increment
    }

    @Test
    void cancelRsvp_notFound_throwsNotFound() {
        when(participationRepo.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancelRsvp(eventId, userId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelRsvp_wasGoing_decrements() {
        when(participationRepo.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.of(participation("going")));
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event(10)));
        service.cancelRsvp(eventId, userId);
        verify(participationRepo).delete(any(EventParticipation.class));
        verify(eventRepo).save(any());
    }

    @Test
    void cancelRsvp_notGoing_noDecrement() {
        when(participationRepo.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.of(participation("maybe")));
        service.cancelRsvp(eventId, userId);
        verify(participationRepo).delete(any(EventParticipation.class));
        verify(eventRepo, never()).save(any());
    }

    // ─── Elite ─────────────────────────────────────────────────────────────────

    @Test
    void eliteActiveUpcoming_andAllElite_map() {
        // Défaut SUPERADMIN (visibleTenantIdsOrNull==null) → aucun filtre, tous les events passent.
        when(eventRepo.findEliteActiveUpcoming()).thenReturn(List.of(event(null)));
        when(eventRepo.findAllElite()).thenReturn(List.of(event(null)));
        assertThat(service.findEliteActiveUpcoming()).hasSize(1);
        assertThat(service.findAllElite()).hasSize(1);
    }

    /** Event de tenant T (tenantId renseigné), pour tester le filtre de périmètre des listes Elite. */
    private Event eliteEvent(UUID tenantId) {
        Event e = new Event(UUID.randomUUID(), new Tenant(tenantId, "T", "t"), "Gala", Instant.now().plusSeconds(86400));
        ReflectionTestUtils.setField(e, "tenantId", tenantId);
        return e;
    }

    @Test
    void eliteLists_filteredByVisibleScope() {
        // Caller membre d'un seul tenant visible : seuls les events de ce tenant sont retournés ;
        // ceux d'un programme hors périmètre sont filtrés (fuite de périmètre fermée).
        UUID visibleTenant = UUID.randomUUID();
        UUID hiddenTenant = UUID.randomUUID();
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(Set.of(visibleTenant));
        when(eventRepo.findEliteActiveUpcoming())
            .thenReturn(List.of(eliteEvent(visibleTenant), eliteEvent(hiddenTenant)));
        when(eventRepo.findAllElite())
            .thenReturn(List.of(eliteEvent(visibleTenant), eliteEvent(hiddenTenant)));
        assertThat(service.findEliteActiveUpcoming()).hasSize(1);
        assertThat(service.findAllElite()).hasSize(1);
    }

    @Test
    void eliteLists_superadminBypass_returnsAll() {
        // SUPERADMIN (visible==null) → aucun filtre, tous les events (multi-tenant) sont retournés.
        when(tenantScope.visibleTenantIdsOrNull()).thenReturn(null);
        when(eventRepo.findEliteActiveUpcoming())
            .thenReturn(List.of(eliteEvent(UUID.randomUUID()), eliteEvent(UUID.randomUUID())));
        when(eventRepo.findAllElite())
            .thenReturn(List.of(eliteEvent(UUID.randomUUID()), eliteEvent(UUID.randomUUID())));
        assertThat(service.findEliteActiveUpcoming()).hasSize(2);
        assertThat(service.findAllElite()).hasSize(2);
    }
}
