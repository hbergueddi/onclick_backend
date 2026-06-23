package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.shared.events.PromoApprovedEvent;
import com.onesley.oneclick.shared.events.PromoAudienceResolvedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires de {@link PromoApprovedListener} — résout l'audience puis republie l'event de dispatch. */
@ExtendWith(MockitoExtension.class)
class PromoApprovedListenerTest {

    @Mock PromoAudienceResolver resolver;
    @Mock ApplicationEventPublisher events;
    @InjectMocks PromoApprovedListener listener;
    @Captor ArgumentCaptor<PromoAudienceResolvedEvent> captor;

    @Test
    void onPromoApproved_resolvesSegment_andRepublishesWithAudience() {
        UUID req = UUID.randomUUID();
        UUID rid = UUID.randomUUID();
        List<UUID> audience = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(resolver.resolve(rid, "fideles")).thenReturn(audience);

        listener.onPromoApproved(new PromoApprovedEvent(
            req, rid, "fideles", "Titre", "Corps", "/pocket/promos", Instant.now()));

        verify(resolver).resolve(rid, "fideles");
        verify(events).publishEvent(captor.capture());
        PromoAudienceResolvedEvent e = captor.getValue();
        assertThat(e.requestId()).isEqualTo(req);
        assertThat(e.userIds()).isEqualTo(audience);
        assertThat(e.title()).isEqualTo("Titre");
        assertThat(e.body()).isEqualTo("Corps");
        assertThat(e.link()).isEqualTo("/pocket/promos");
    }
}
