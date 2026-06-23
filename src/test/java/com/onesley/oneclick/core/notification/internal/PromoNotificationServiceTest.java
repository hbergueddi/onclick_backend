package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.PromoNotificationDtos.PromoRequestCreateDto;
import com.onesley.oneclick.core.notification.api.PromoNotificationDtos.PromoRequestReviewDto;
import com.onesley.oneclick.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.onesley.oneclick.shared.events.PromoApprovedEvent;
import com.onesley.oneclick.shared.events.PromoRequestReviewedEvent;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link PromoNotificationService} (L3 — core.notification).
 * findAll (3 branches) / findById / create / review / markSent / stats (native).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PromoNotificationServiceTest {

    @Mock PromoNotificationRequestRepository requestRepo;
    @Mock EntityManager em;
    @Mock Query query;
    @Mock ApplicationEventPublisher events;
    @InjectMocks PromoNotificationService service;

    @Captor ArgumentCaptor<Object> eventCaptor;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(requestRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private PromoNotificationRequest req() {
        PromoNotificationRequest p = new PromoNotificationRequest();
        p.setTitle("Promo"); p.setStatus("pending");
        return p;
    }

    @Test
    void findAll_threeBranches() {
        when(requestRepo.findByStatus(any())).thenReturn(List.of(req()));
        when(requestRepo.findByRestaurant(any())).thenReturn(List.of(req()));
        when(requestRepo.findAllActive()).thenReturn(List.of(req(), req()));
        assertThat(service.findAll("pending", null)).hasSize(1);
        assertThat(service.findAll(null, UUID.randomUUID())).hasSize(1);
        assertThat(service.findAll(null, null)).hasSize(2);
    }

    @Test
    void findById_notFoundAndFound() {
        when(requestRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        PromoNotificationRequest p = req();
        when(requestRepo.findById(any())).thenReturn(Optional.of(p));
        assertThat(service.findById(UUID.randomUUID())).isNotNull();
    }

    @Test
    void create_withSegmentAndDefault() {
        assertThat(service.create(new PromoRequestCreateDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "T", "B", "ruby", UUID.randomUUID()))).isNotNull();
        assertThat(service.create(new PromoRequestCreateDto(UUID.randomUUID(), UUID.randomUUID(), null,
            "T", "B", null, UUID.randomUUID()))).isNotNull();
    }

    @Test
    void review_notFoundAndSuccess() {
        when(requestRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.review(UUID.randomUUID(),
            new PromoRequestReviewDto("approved", UUID.randomUUID(), null))).isInstanceOf(NotFoundException.class);
        PromoNotificationRequest p = req();
        when(requestRepo.findById(any())).thenReturn(Optional.of(p));
        var dto = service.review(UUID.randomUUID(), new PromoRequestReviewDto("rejected", UUID.randomUUID(), "raison"));
        assertThat(dto.status()).isEqualTo("rejected");
    }

    // ── B12 — verdict de modération → event PromoRequestReviewedEvent au demandeur ──
    @Test
    void review_rejected_publishesReviewedEvent_notApprovedEvent() {
        UUID requester = UUID.randomUUID();
        PromoNotificationRequest p = req();
        p.setRequestedBy(requester);
        when(requestRepo.findById(any())).thenReturn(Optional.of(p));

        service.review(UUID.randomUUID(), new PromoRequestReviewDto("rejected", UUID.randomUUID(), "hors charte"));

        verify(events).publishEvent(eventCaptor.capture());
        Object ev = eventCaptor.getValue();
        assertThat(ev).isInstanceOf(PromoRequestReviewedEvent.class);
        PromoRequestReviewedEvent reviewed = (PromoRequestReviewedEvent) ev;
        assertThat(reviewed.requesterId()).isEqualTo(requester);
        assertThat(reviewed.approved()).isFalse();
        assertThat(reviewed.rejectionReason()).isEqualTo("hors charte");
    }

    @Test
    void review_approved_publishesBothApprovedAndReviewedEvents() {
        UUID requester = UUID.randomUUID();
        PromoNotificationRequest p = req();
        p.setRequestedBy(requester);
        when(requestRepo.findById(any())).thenReturn(Optional.of(p));

        service.review(UUID.randomUUID(), new PromoRequestReviewDto("approved", UUID.randomUUID(), null));

        verify(events, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());
        List<Object> all = eventCaptor.getAllValues();
        assertThat(all).anyMatch(e -> e instanceof PromoApprovedEvent); // fan-out FCM (existant)
        PromoRequestReviewedEvent reviewed = all.stream()
            .filter(e -> e instanceof PromoRequestReviewedEvent)
            .map(e -> (PromoRequestReviewedEvent) e).findFirst().orElseThrow();
        assertThat(reviewed.requesterId()).isEqualTo(requester);
        assertThat(reviewed.approved()).isTrue();
    }

    @Test
    void markSent_notFoundAndSuccess() {
        when(requestRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markSent(UUID.randomUUID(), 10, null)).isInstanceOf(NotFoundException.class);
        PromoNotificationRequest p = req();
        when(requestRepo.findById(any())).thenReturn(Optional.of(p));
        var dto = service.markSent(UUID.randomUUID(), 42, "partial error");
        assertThat(dto.status()).isEqualTo("sent");
        assertThat(dto.pushSentCount()).isEqualTo(42);
    }

    @Test
    void stats_mapsRow() {
        Object[] row = { 100L, 60L, 1500L, 230L, 5000L };
        when(query.getSingleResult()).thenReturn(row);
        assertThat(service.stats().totalOffers()).isEqualTo(100L);
    }
}
