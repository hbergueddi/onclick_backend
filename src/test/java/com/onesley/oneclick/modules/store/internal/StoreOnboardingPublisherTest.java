package com.onesley.oneclick.modules.store.internal;

import com.onesley.oneclick.modules.store.api.StoreOnboardingDtos.OnboardingRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * BE-5 — tests unitaires de {@link StoreOnboardingPublisher} (push STOMP file admin onboarding).
 */
@ExtendWith(MockitoExtension.class)
class StoreOnboardingPublisherTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks StoreOnboardingPublisher publisher;

    private OnboardingRequestDto dto() {
        return new OnboardingRequestDto(
            UUID.randomUUID(), null, "Resto", null, "Casablanca", null, null,
            "Ada", "Lovelace", "a@x.ma", null, "pending", null, null, null, null, Instant.now(),
            null, null, null, null, null, null, null, null, List.of(), null, null);
    }

    @Test
    void publishNew_sendsToAdminOnboardingTopic() {
        OnboardingRequestDto d = dto();
        publisher.publishNew(d);
        verify(messagingTemplate).convertAndSend(eq("/topic/admin/onboarding"), eq((Object) d));
    }

    @Test
    void publishNew_swallowsPushFailure_bestEffort() {
        doThrow(new RuntimeException("broker down")).when(messagingTemplate).convertAndSend(any(String.class), any(Object.class));
        assertThatCode(() -> publisher.publishNew(dto())).doesNotThrowAnyException();
    }
}
