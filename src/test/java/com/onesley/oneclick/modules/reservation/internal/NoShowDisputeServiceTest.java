package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.reservation.api.NoShowDisputeDtos.CreateDisputeDto;
import com.onesley.oneclick.modules.reservation.api.NoShowDisputeDtos.NoShowDisputeDto;
import com.onesley.oneclick.modules.reservation.api.NoShowDisputeDtos.ResolveDisputeDto;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.NoShowDisputeResolvedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés (Mockito, sans contexte Spring) de {@link NoShowDisputeService}
 * (Feature #3). Couvre :
 * <ul>
 *   <li>calcul de phase (resto / support / expired) avec {@link Clock} fixe ;</li>
 *   <li>éligibilité création : not-owner, not-no_show, late_cancellation, expired,
 *       déjà disputé (pending/accepted), re-contestation sans photo, re-contestation OK ;</li>
 *   <li>résolution accept/refuse + publication de {@link NoShowDisputeResolvedEvent} + accès par phase.</li>
 * </ul>
 *
 * <p>L'horloge est fixée à {@code NOW} ; on positionne {@code no_show_marked_at} relatif
 * à {@code NOW} pour piloter la phase de façon déterministe (reproductible).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NoShowDisputeServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-04T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final UUID RESTAURANT = UUID.randomUUID();
    private static final UUID RESERVATION = UUID.randomUUID();

    @Mock NoShowDisputeRepository disputeRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock RestaurantAccessGuard restaurantAccessGuard;
    @Mock ApplicationEventPublisher eventPublisher;

    NoShowDisputeService service;

    @BeforeEach
    void setup() {
        service = new NoShowDisputeService(
            disputeRepository, reservationRepository, restaurantAccessGuard, eventPublisher, FIXED_CLOCK);
        when(disputeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Réservation no_show marquée il y a {@code minutesAgo} minutes (relatif à l'horloge fixe). */
    private Reservation noShowReservation(long minutesAgo, boolean lateCancellation) {
        Reservation r = new Reservation(RESERVATION, null, null, RESTAURANT,
            NOW.minusSeconds(3 * 86400), 2);
        ReflectionTestUtils.setField(r, "clientId", CLIENT);
        r.setStatus("no_show");
        r.setLateCancellation(lateCancellation);
        r.setNoShowMarkedAt(NOW.minusSeconds(minutesAgo * 60));
        return r;
    }

    private NoShowDispute disputeWithStatus(String status, String phase) {
        NoShowDispute d = new NoShowDispute(UUID.randomUUID(), RESERVATION, CLIENT, RESTAURANT,
            phase, "raison", null);
        d.setStatus(status);
        return d;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Calcul de phase
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void computePhase_resto_within1h() {
        assertThat(service.computePhase(NOW.minusSeconds(30 * 60))).isEqualTo(NoShowDisputeService.PHASE_RESTO);
        assertThat(service.computePhase(NOW)).isEqualTo(NoShowDisputeService.PHASE_RESTO);
    }

    @Test
    void computePhase_support_between1hAnd48h() {
        assertThat(service.computePhase(NOW.minusSeconds(2 * 3600))).isEqualTo(NoShowDisputeService.PHASE_SUPPORT);
        assertThat(service.computePhase(NOW.minusSeconds(47 * 3600))).isEqualTo(NoShowDisputeService.PHASE_SUPPORT);
    }

    @Test
    void computePhase_expired_after48h_orNull() {
        assertThat(service.computePhase(NOW.minusSeconds(49 * 3600))).isEqualTo(NoShowDisputeService.PHASE_EXPIRED);
        assertThat(service.computePhase(null)).isEqualTo(NoShowDisputeService.PHASE_EXPIRED);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Éligibilité création
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void create_reservationNotFound_throwsNotFound() {
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = staticSecurity(CLIENT, false)) {
            assertThatThrownBy(() -> service.create(RESERVATION, new CreateDisputeDto("r", null)))
                .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void create_notOwner_throwsForbidden() {
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.of(noShowReservation(30, false)));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(OTHER_USER, false)) {
            assertThatThrownBy(() -> service.create(RESERVATION, new CreateDisputeDto("r", null)))
                .isInstanceOf(ForbiddenException.class);
        }
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void create_notNoShow_throwsBadRequest() {
        Reservation r = noShowReservation(30, false);
        r.setStatus("confirmed");
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.of(r));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(CLIENT, false)) {
            assertThatThrownBy(() -> service.create(RESERVATION, new CreateDisputeDto("r", null)))
                .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void create_lateCancellation_throwsBadRequest_notContestable() {
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.of(noShowReservation(30, true)));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(CLIENT, false)) {
            assertThatThrownBy(() -> service.create(RESERVATION, new CreateDisputeDto("r", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("annulation tardive");
        }
    }

    @Test
    void create_expiredPhase_throwsBadRequest() {
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.of(noShowReservation(49 * 60, false)));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(CLIENT, false)) {
            assertThatThrownBy(() -> service.create(RESERVATION, new CreateDisputeDto("r", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("48h");
        }
    }

    @Test
    void create_alreadyPending_throwsConflict() {
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.of(noShowReservation(30, false)));
        when(disputeRepository.findByReservationIdOrderByCreatedAtDesc(RESERVATION))
            .thenReturn(List.of(disputeWithStatus("pending", "resto")));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(CLIENT, false)) {
            assertThatThrownBy(() -> service.create(RESERVATION, new CreateDisputeDto("r", null)))
                .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void create_alreadyAccepted_throwsConflict() {
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.of(noShowReservation(30, false)));
        when(disputeRepository.findByReservationIdOrderByCreatedAtDesc(RESERVATION))
            .thenReturn(List.of(disputeWithStatus("accepted", "resto")));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(CLIENT, false)) {
            assertThatThrownBy(() -> service.create(RESERVATION, new CreateDisputeDto("r", null)))
                .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void create_recontestAfterRefused_withoutPhoto_throwsBadRequest() {
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.of(noShowReservation(30, false)));
        when(disputeRepository.findByReservationIdOrderByCreatedAtDesc(RESERVATION))
            .thenReturn(List.of(disputeWithStatus("refused", "resto")));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(CLIENT, false)) {
            assertThatThrownBy(() -> service.create(RESERVATION, new CreateDisputeDto("encore", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("photo");
        }
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void create_recontestAfterRefused_withPhoto_succeeds() {
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.of(noShowReservation(30, false)));
        when(disputeRepository.findByReservationIdOrderByCreatedAtDesc(RESERVATION))
            .thenReturn(List.of(disputeWithStatus("refused", "resto")));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(CLIENT, false)) {
            NoShowDisputeDto out = service.create(RESERVATION,
                new CreateDisputeDto("avec preuve", "https://photos/x.jpg"));
            assertThat(out).isNotNull();
            assertThat(out.escalationPhase()).isEqualTo(NoShowDisputeService.PHASE_RESTO);
            assertThat(out.photoUrl()).isEqualTo("https://photos/x.jpg");
            assertThat(out.status()).isEqualTo("pending");
        }
        verify(disputeRepository).save(any(NoShowDispute.class));
    }

    @Test
    void create_firstDispute_restoPhase_succeeds() {
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.of(noShowReservation(30, false)));
        when(disputeRepository.findByReservationIdOrderByCreatedAtDesc(RESERVATION)).thenReturn(List.of());
        try (MockedStatic<SecurityHelper> sec = staticSecurity(CLIENT, false)) {
            NoShowDisputeDto out = service.create(RESERVATION, new CreateDisputeDto("j'étais présent", null));
            assertThat(out.escalationPhase()).isEqualTo(NoShowDisputeService.PHASE_RESTO);
            assertThat(out.clientId()).isEqualTo(CLIENT);
            assertThat(out.restaurantId()).isEqualTo(RESTAURANT);
        }
    }

    @Test
    void create_firstDispute_supportPhase_freezesPhaseAtSupport() {
        when(reservationRepository.findById(RESERVATION)).thenReturn(Optional.of(noShowReservation(120, false)));
        when(disputeRepository.findByReservationIdOrderByCreatedAtDesc(RESERVATION)).thenReturn(List.of());
        try (MockedStatic<SecurityHelper> sec = staticSecurity(CLIENT, false)) {
            NoShowDisputeDto out = service.create(RESERVATION, new CreateDisputeDto("2h plus tard", null));
            assertThat(out.escalationPhase()).isEqualTo(NoShowDisputeService.PHASE_SUPPORT);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Résolution
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void resolve_disputeNotFound_throwsNotFound() {
        when(disputeRepository.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = staticSecurity(OTHER_USER, true)) {
            assertThatThrownBy(() -> service.resolve(UUID.randomUUID(), new ResolveDisputeDto("accepted", null)))
                .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void resolve_alreadyResolved_throwsConflict() {
        NoShowDispute d = disputeWithStatus("accepted", "resto");
        when(disputeRepository.findById(d.getId())).thenReturn(Optional.of(d));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(OTHER_USER, true)) {
            assertThatThrownBy(() -> service.resolve(d.getId(), new ResolveDisputeDto("refused", null)))
                .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void resolve_accepted_publishesEventAcceptedTrue() {
        NoShowDispute d = disputeWithStatus("pending", "resto");
        when(disputeRepository.findById(d.getId())).thenReturn(Optional.of(d));
        when(restaurantAccessGuard.isAdminOrActiveStaffOf(RESTAURANT)).thenReturn(true);
        try (MockedStatic<SecurityHelper> sec = staticSecurity(OTHER_USER, false)) {
            NoShowDisputeDto out = service.resolve(d.getId(), new ResolveDisputeDto("accepted", "preuve OK"));
            assertThat(out.status()).isEqualTo("accepted");
            assertThat(out.resolutionNote()).isEqualTo("preuve OK");
        }
        ArgumentCaptor<NoShowDisputeResolvedEvent> cap = ArgumentCaptor.forClass(NoShowDisputeResolvedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().accepted()).isTrue();
        assertThat(cap.getValue().clientId()).isEqualTo(CLIENT);
        assertThat(cap.getValue().reservationId()).isEqualTo(RESERVATION);
    }

    @Test
    void resolve_refused_publishesEventAcceptedFalse_keepsPenalty() {
        NoShowDispute d = disputeWithStatus("pending", "resto");
        when(disputeRepository.findById(d.getId())).thenReturn(Optional.of(d));
        when(restaurantAccessGuard.isAdminOrActiveStaffOf(RESTAURANT)).thenReturn(true);
        try (MockedStatic<SecurityHelper> sec = staticSecurity(OTHER_USER, false)) {
            NoShowDisputeDto out = service.resolve(d.getId(), new ResolveDisputeDto("refused", "pas de preuve"));
            assertThat(out.status()).isEqualTo("refused");
        }
        ArgumentCaptor<NoShowDisputeResolvedEvent> cap = ArgumentCaptor.forClass(NoShowDisputeResolvedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().accepted()).isFalse();
    }

    @Test
    void resolve_restoPhase_nonStaffNonAdmin_throwsForbidden() {
        NoShowDispute d = disputeWithStatus("pending", "resto");
        when(disputeRepository.findById(d.getId())).thenReturn(Optional.of(d));
        when(restaurantAccessGuard.isAdminOrActiveStaffOf(RESTAURANT)).thenReturn(false);
        try (MockedStatic<SecurityHelper> sec = staticSecurity(OTHER_USER, false)) {
            assertThatThrownBy(() -> service.resolve(d.getId(), new ResolveDisputeDto("accepted", null)))
                .isInstanceOf(ForbiddenException.class);
        }
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resolve_supportPhase_restaurantStaff_throwsForbidden_onlyAdminSupport() {
        NoShowDispute d = disputeWithStatus("pending", "support");
        when(disputeRepository.findById(d.getId())).thenReturn(Optional.of(d));
        // Même si staff actif du resto, en phase support seul admin/support tranche.
        when(restaurantAccessGuard.isAdminOrActiveStaffOf(RESTAURANT)).thenReturn(true);
        try (MockedStatic<SecurityHelper> sec = staticSecurity(OTHER_USER, false)) {
            assertThatThrownBy(() -> service.resolve(d.getId(), new ResolveDisputeDto("accepted", null)))
                .isInstanceOf(ForbiddenException.class);
        }
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resolve_supportPhase_admin_succeeds() {
        NoShowDispute d = disputeWithStatus("pending", "support");
        when(disputeRepository.findById(d.getId())).thenReturn(Optional.of(d));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(OTHER_USER, true)) {
            NoShowDisputeDto out = service.resolve(d.getId(), new ResolveDisputeDto("accepted", null));
            assertThat(out.status()).isEqualTo("accepted");
        }
        verify(eventPublisher).publishEvent(any(NoShowDisputeResolvedEvent.class));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  findAll scoping
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void findAll_admin_returnsAll_withStatusFilter() {
        when(disputeRepository.findAllByOrderByCreatedAtDesc())
            .thenReturn(List.of(disputeWithStatus("pending", "resto"), disputeWithStatus("refused", "support")));
        try (MockedStatic<SecurityHelper> sec = staticSecurity(OTHER_USER, true)) {
            assertThat(service.findAll(null, null)).hasSize(2);
            assertThat(service.findAll("pending", null)).hasSize(1);
            assertThat(service.findAll(null, "support")).hasSize(1);
        }
    }

    private static MockedStatic<SecurityHelper> staticSecurity(UUID currentUser, boolean admin) {
        MockedStatic<SecurityHelper> sec = org.mockito.Mockito.mockStatic(SecurityHelper.class);
        sec.when(SecurityHelper::currentUserId).thenReturn(currentUser);
        sec.when(SecurityHelper::isAdmin).thenReturn(admin);
        return sec;
    }
}
