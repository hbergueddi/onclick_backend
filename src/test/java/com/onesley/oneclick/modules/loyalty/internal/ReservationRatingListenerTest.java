package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.shared.events.NoShowDisputeResolvedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés (Mockito) du listener loyalty {@link ReservationRatingListener}.
 *
 * <p>Vérifie que la pénalité de réputation est APPLIQUÉE / REVERSÉE selon les events,
 * sans appel direct reservation→loyalty (le listener vit dans loyalty et consomme les
 * events shared). On capture le {@code delta} passé à {@code recordRating}.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ReservationRatingListenerTest {

    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID RESERVATION = UUID.randomUUID();
    private static final UUID RESTAURANT = UUID.randomUUID();

    @Mock LoyaltyExtensionService loyaltyExtensionService;
    @Mock ClientScoreConfigRepository scoreConfigRepo;
    @InjectMocks ReservationRatingListener listener;

    @BeforeEach
    void setup() {
        // Config singleton par défaut : penaliteNoShow=0.5 (parité legacy, V102), gainParPalier=0.1.
        when(scoreConfigRepo.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(new ClientScoreConfig()));
    }

    private ReservationStatusChangedEvent statusEvent(String newStatus) {
        return new ReservationStatusChangedEvent(
            RESERVATION, CLIENT, RESTAURANT, UUID.randomUUID(), "confirmed", newStatus, null, Instant.now());
    }

    @Test
    void noShow_appliesNegativePenalty() {
        listener.onReservationStatusChanged(statusEvent("no_show"));

        ArgumentCaptor<BigDecimal> delta = ArgumentCaptor.forClass(BigDecimal.class);
        verify(loyaltyExtensionService).recordRating(eq(CLIENT), eq(RESERVATION), delta.capture(), eq("no_show"));
        assertThat(delta.getValue()).isEqualByComparingTo(new BigDecimal("-0.5"));
    }

    @Test
    void honored_appliesPositiveBonus() {
        listener.onReservationStatusChanged(statusEvent("honored"));

        ArgumentCaptor<BigDecimal> delta = ArgumentCaptor.forClass(BigDecimal.class);
        verify(loyaltyExtensionService).recordRating(eq(CLIENT), eq(RESERVATION), delta.capture(), eq("honored"));
        assertThat(delta.getValue()).isEqualByComparingTo(new BigDecimal("0.1"));
    }

    @Test
    void otherStatus_noRating() {
        listener.onReservationStatusChanged(statusEvent("confirmed"));
        listener.onReservationStatusChanged(statusEvent("cancelled"));
        verify(loyaltyExtensionService, never()).recordRating(any(), any(), any(), any());
    }

    @Test
    void nullClient_skips() {
        listener.onReservationStatusChanged(new ReservationStatusChangedEvent(
            RESERVATION, null, RESTAURANT, UUID.randomUUID(), "confirmed", "no_show", null, Instant.now()));
        verify(loyaltyExtensionService, never()).recordRating(any(), any(), any(), any());
    }

    @Test
    void disputeAccepted_reversesPenalty_positiveDelta() {
        listener.onDisputeResolved(new NoShowDisputeResolvedEvent(
            UUID.randomUUID(), RESERVATION, CLIENT, RESTAURANT, true));

        ArgumentCaptor<BigDecimal> delta = ArgumentCaptor.forClass(BigDecimal.class);
        verify(loyaltyExtensionService).recordRating(eq(CLIENT), eq(RESERVATION), delta.capture(), eq("dispute_accepted"));
        assertThat(delta.getValue()).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    void disputeRefused_noReversal() {
        listener.onDisputeResolved(new NoShowDisputeResolvedEvent(
            UUID.randomUUID(), RESERVATION, CLIENT, RESTAURANT, false));
        verify(loyaltyExtensionService, never()).recordRating(any(), any(), any(), any());
    }
}
