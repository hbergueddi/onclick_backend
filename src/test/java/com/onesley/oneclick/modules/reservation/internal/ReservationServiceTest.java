package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.reservation.api.ReservationCreateDto;
import com.onesley.oneclick.modules.reservation.api.ReservationDto;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link ReservationService} (L3 — modules.reservation).
 * Couvre la logique métier dense : create (garde date future + branches table/notes),
 * changeStatus (validation, not-found, no-op, succès avec/sans acteur), counts, findAll,
 * batch findAccessibleByIds. (Les chemins d'accès statiques SecurityHelper sont
 * couverts en intégration.)
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository repository;
    @Mock ReservationStatusHistoryRepository historyRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EntityManager entityManager;
    @InjectMocks ReservationService service;

    @BeforeEach
    void injectEntityManager() {
        // @InjectMocks fait l'injection par constructeur (3 args) mais PAS le champ
        // @PersistenceContext entityManager -> on l'injecte à la main.
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    private ReservationCreateDto createDto(Instant at, UUID tableId, String notes) {
        return new ReservationCreateDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            tableId, null, at, 4, notes);
    }

    private Reservation reservation(String status) {
        Reservation r = new Reservation(UUID.randomUUID(), null, null,
            UUID.randomUUID(), Instant.now().plusSeconds(86400), 2);
        r.setStatus(status);
        return r;
    }

    // ─── create ────────────────────────────────────────────────────────────────

    @Test
    void create_pastDate_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(createDto(Instant.now().minusSeconds(3600), null, null)))
            .isInstanceOf(BadRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void create_success_savesReservation_history_andPublishesEvent() {
        when(entityManager.getReference(eq(Tenant.class), any()))
            .thenReturn(new Tenant(UUID.randomUUID(), "T", "t"));
        when(entityManager.getReference(eq(User.class), any()))
            .thenReturn(new User(UUID.randomUUID(), null, "c@x.ma", "h", "Cli", "Ent"));
        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationDto dto = service.create(createDto(Instant.now().plusSeconds(86400), UUID.randomUUID(), "fenêtre svp"));

        assertThat(dto).isNotNull();
        assertThat(dto.status()).isEqualTo("pending");
        verify(repository).save(any(Reservation.class));
        verify(historyRepository).save(any(ReservationStatusHistory.class));
        verify(eventPublisher).publishEvent(any(ReservationCreatedEvent.class));
    }

    @Test
    void create_withoutTableNorNotes_success() {
        when(entityManager.getReference(eq(Tenant.class), any())).thenReturn(new Tenant(UUID.randomUUID(), "T", "t"));
        when(entityManager.getReference(eq(User.class), any())).thenReturn(new User(UUID.randomUUID(), null, "c@x.ma", "h", "C", "E"));
        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationDto dto = service.create(createDto(Instant.now().plusSeconds(3600), null, null));

        assertThat(dto).isNotNull();
        verify(eventPublisher).publishEvent(any(ReservationCreatedEvent.class));
    }

    // ─── changeStatus ────────────────────────────────────────────────────────────

    @Test
    void changeStatus_invalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> service.changeStatus(UUID.randomUUID(), "bogus", null, null))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void changeStatus_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeStatus(id, "confirmed", null, null))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void changeStatus_sameStatus_isNoOp() {
        Reservation r = reservation("pending");
        when(repository.findById(any())).thenReturn(Optional.of(r));

        ReservationDto dto = service.changeStatus(UUID.randomUUID(), "pending", null, null);

        assertThat(dto.status()).isEqualTo("pending");
        verify(historyRepository, never()).save(any());
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_withActor_updatesStatus_logsHistory_publishesEvent() {
        Reservation r = reservation("pending");
        UUID actorId = UUID.randomUUID();
        when(repository.findById(any())).thenReturn(Optional.of(r));
        when(entityManager.getReference(eq(User.class), eq(actorId)))
            .thenReturn(new User(actorId, null, "a@x.ma", "h", "Act", "Or"));
        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationDto dto = service.changeStatus(UUID.randomUUID(), "confirmed", actorId, "table prête");

        assertThat(dto.status()).isEqualTo("confirmed");
        assertThat(r.getStatus()).isEqualTo("confirmed");
        verify(historyRepository).save(any(ReservationStatusHistory.class));
        verify(eventPublisher).publishEvent(any(ReservationStatusChangedEvent.class));
    }

    @Test
    void changeStatus_nullActor_success() {
        Reservation r = reservation("pending");
        when(repository.findById(any())).thenReturn(Optional.of(r));
        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationDto dto = service.changeStatus(UUID.randomUUID(), "cancelled", null, "client annule");

        assertThat(dto.status()).isEqualTo("cancelled");
        verify(historyRepository).save(any(ReservationStatusHistory.class));
        verify(eventPublisher).publishEvent(any(ReservationStatusChangedEvent.class));
        verify(entityManager, never()).getReference(eq(User.class), any()); // pas d'acteur résolu
    }

    // ─── Feature #4 — no_show : lateCancellation + no_show_marked_at ──────────────

    @Test
    void changeStatus_noShow_lateCancellationTrue_persistsFlag_andStampsMarkedAt() {
        Reservation r = reservation("confirmed");
        when(repository.findById(any())).thenReturn(Optional.of(r));
        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationDto dto = service.changeStatus(UUID.randomUUID(), "no_show", null, "absent", true);

        assertThat(dto.status()).isEqualTo("no_show");
        assertThat(dto.lateCancellation()).isTrue();
        assertThat(r.isLateCancellation()).isTrue();
        assertThat(r.getNoShowMarkedAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(ReservationStatusChangedEvent.class));
    }

    @Test
    void changeStatus_noShow_defaultLateCancellationFalse_butStampsMarkedAt() {
        Reservation r = reservation("confirmed");
        when(repository.findById(any())).thenReturn(Optional.of(r));
        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        // overload sans lateCancellation → false par défaut, mais no_show_marked_at posé.
        ReservationDto dto = service.changeStatus(UUID.randomUUID(), "no_show", null, "absent");

        assertThat(dto.lateCancellation()).isFalse();
        assertThat(r.isLateCancellation()).isFalse();
        assertThat(r.getNoShowMarkedAt()).isNotNull();
    }

    @Test
    void changeStatus_nonNoShow_doesNotStampMarkedAt() {
        Reservation r = reservation("pending");
        when(repository.findById(any())).thenReturn(Optional.of(r));
        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        service.changeStatus(UUID.randomUUID(), "confirmed", null, null, true);

        assertThat(r.getNoShowMarkedAt()).isNull(); // lateCancellation ignoré hors no_show
        assertThat(r.isLateCancellation()).isFalse();
    }

    // ─── counts / findAll / batch ────────────────────────────────────────────────

    @Test
    void countReservationsByRestaurant_sinceDaysAndStatus() {
        UUID rid = UUID.randomUUID();
        when(repository.countByRestaurantGrouped(any(Instant.class), eq("confirmed")))
            .thenReturn(List.<Object[]>of(new Object[]{rid, 5L}));

        Map<UUID, Long> out = service.countReservationsByRestaurant(7, "confirmed");

        assertThat(out).containsEntry(rid, 5L);
    }

    @Test
    void countReservationsByRestaurant_allTime_blankStatus() {
        UUID rid = UUID.randomUUID();
        when(repository.countByRestaurantGrouped(isNull(), isNull()))
            .thenReturn(List.<Object[]>of(new Object[]{rid, 3L}));

        Map<UUID, Long> out = service.countReservationsByRestaurant(0, "  ");

        assertThat(out).containsEntry(rid, 3L);
    }

    @Test
    void findAll_delegatesToRepository() {
        when(repository.findAllWithJoins(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        Page<ReservationDto> page = service.findAll(null, null, null, null, null, 0, 20);
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void findAll_forwardsDateWindowToRepository() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(repository.findAllWithJoins(isNull(), isNull(), isNull(), eq(from), eq(to), any()))
            .thenReturn(Page.empty());
        service.findAll(null, null, null, from, to, 0, 20);
        verify(repository).findAllWithJoins(isNull(), isNull(), isNull(), eq(from), eq(to), any());
    }

    @Test
    void findAccessibleByIds_nullOrEmpty_returnsEmpty() {
        assertThat(service.findAccessibleByIds(null)).isEmpty();
        assertThat(service.findAccessibleByIds(List.of())).isEmpty();
        verify(repository, never()).findAllById(any());
    }

    // ─── P1 (anti-N+1 shim) — findByRestaurants ─────────────────────────────

    @Test
    void findByRestaurants_nullOrEmpty_returnsEmpty_noRepoCall() {
        assertThat(service.findByRestaurants(null, 500)).isEmpty();
        assertThat(service.findByRestaurants(List.of(), 500)).isEmpty();
        verify(repository, never()).findEnrichedByRestaurantIds(any(), anyInt());
    }

    @Test
    void findByRestaurants_mapsEnrichedViewsToDto() {
        UUID rid = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ReservationWithJoinsView v = mock(ReservationWithJoinsView.class);
        when(v.getId()).thenReturn(id);
        when(v.getRestaurantId()).thenReturn(rid);
        when(v.getStatus()).thenReturn("confirmée");
        when(v.getClientFirstName()).thenReturn("Ada");
        when(repository.findEnrichedByRestaurantIds(List.of(rid), 500)).thenReturn(List.of(v));

        var out = service.findByRestaurants(List.of(rid), 500);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).id()).isEqualTo(id);
        assertThat(out.get(0).restaurantId()).isEqualTo(rid);
        assertThat(out.get(0).status()).isEqualTo("confirmée");
        assertThat(out.get(0).clientFirstName()).isEqualTo("Ada");
    }

    // ─── ITEM 1 — noShowMarkedAt exposé dans le DTO ───────────────────────────

    @Test
    void toDto_exposesNoShowMarkedAt_whenNoShow() {
        Instant markedAt = Instant.parse("2026-06-01T12:00:00Z");
        Reservation r = reservation("no_show");
        r.setNoShowMarkedAt(markedAt);

        ReservationDto dto = r.toDto();

        assertThat(dto.status()).isEqualTo("no_show");
        assertThat(dto.noShowMarkedAt()).isEqualTo(markedAt);
    }

    @Test
    void toDto_noShowMarkedAtNull_whenNotMarked() {
        // Réservation non marquée absente → champ null (pas de fenêtre de contestation).
        ReservationDto dto = reservation("confirmée").toDto();
        assertThat(dto.noShowMarkedAt()).isNull();
    }

    @Test
    void joinsViewMapper_propagatesNoShowMarkedAt() {
        // Le chemin lecture groupée (findByRestaurants → projection) doit aussi
        // porter noShowMarkedAt jusqu'au DTO.
        Instant markedAt = Instant.parse("2026-06-02T09:30:00Z");
        UUID rid = UUID.randomUUID();
        ReservationWithJoinsView v = mock(ReservationWithJoinsView.class);
        when(v.getId()).thenReturn(UUID.randomUUID());
        when(v.getRestaurantId()).thenReturn(rid);
        when(v.getStatus()).thenReturn("no_show");
        when(v.getNoShowMarkedAt()).thenReturn(markedAt);
        when(repository.findEnrichedByRestaurantIds(List.of(rid), 500)).thenReturn(List.of(v));

        var out = service.findByRestaurants(List.of(rid), 500);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).noShowMarkedAt()).isEqualTo(markedAt);
    }
}
