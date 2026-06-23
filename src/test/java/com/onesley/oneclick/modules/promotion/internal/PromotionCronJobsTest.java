package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.shared.events.OfferExpiredEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link PromotionCronJobs#expirePromotions}.
 *
 * <p>Lot B11 : le cron ne fait plus un pur UPDATE bulk muet ; il SELECT les offres au passage
 * actif→expiré (id, resto, titre), UPDATE uniquement celles-là, puis publie un
 * {@link OfferExpiredEvent} par offre (staff du resto résolu via {@link OfferRepository}) → le staff
 * est notifié par {@code NotificationEventHandler}. Ces tests verrouillent le fan-out (1 event par
 * offre AVEC staff, court-circuit si 0 due ou 0 staff).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PromotionCronJobsTest {

    @Mock EntityManager em;
    @Mock Query query;
    @Mock OfferRepository offerRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks PromotionCronJobs cron;

    @Captor ArgumentCaptor<OfferExpiredEvent> offerCaptor;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(cron, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query); // chaînage UPDATE ... IN (:ids)
        lenient().when(query.executeUpdate()).thenReturn(2);
        lenient().when(query.getResultList()).thenReturn(List.of());
    }

    @Test
    void expirePromotions_dueOffers_updatesAndPublishesEventPerOfferWithStaff() {
        UUID o1 = UUID.randomUUID(), resto1 = UUID.randomUUID();
        UUID o2 = UUID.randomUUID(), resto2 = UUID.randomUUID();
        when(query.getResultList()).thenReturn(List.of(
            new Object[]{o1, resto1, "Happy Hour"},
            new Object[]{o2, resto2, "Brunch -20%"}));
        UUID staff1 = UUID.randomUUID();
        when(offerRepository.findStaffRecipientIdsForRestaurant(resto1)).thenReturn(List.of(staff1));
        when(offerRepository.findStaffRecipientIdsForRestaurant(resto2)).thenReturn(List.of(UUID.randomUUID()));

        cron.expirePromotions();

        verify(query).executeUpdate(); // UPDATE exécuté
        verify(events, times(2)).publishEvent(offerCaptor.capture());
        OfferExpiredEvent first = offerCaptor.getAllValues().get(0);
        assertThat(first.offerId()).isEqualTo(o1);
        assertThat(first.restaurantId()).isEqualTo(resto1);
        assertThat(first.title()).isEqualTo("Happy Hour");
        assertThat(first.staffRecipientIds()).containsExactly(staff1);
    }

    @Test
    void expirePromotions_offerWithoutStaff_skipsEvent() {
        UUID o1 = UUID.randomUUID(), resto1 = UUID.randomUUID();
        when(query.getResultList()).thenReturn(
            java.util.Collections.singletonList(new Object[]{o1, resto1, "Promo"}));
        when(offerRepository.findStaffRecipientIdsForRestaurant(resto1)).thenReturn(List.of());

        cron.expirePromotions();

        verify(query).executeUpdate(); // l'offre est tout de même désactivée
        verify(events, never()).publishEvent(any(OfferExpiredEvent.class)); // mais pas de notif (0 staff)
    }

    @Test
    void expirePromotions_noDueOffers_noUpdateNoEvent() {
        when(query.getResultList()).thenReturn(List.of());
        cron.expirePromotions();
        verify(query, never()).executeUpdate();
        verify(events, never()).publishEvent(any(OfferExpiredEvent.class));
        verify(offerRepository, never()).findStaffRecipientIdsForRestaurant(any());
    }
}
