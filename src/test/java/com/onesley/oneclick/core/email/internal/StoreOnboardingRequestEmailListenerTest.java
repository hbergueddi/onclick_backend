package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.shared.events.StoreOnboardingRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires isolés de {@link StoreOnboardingRequestEmailListener} (BE-1, core.email).
 *
 * <p>Vérifie que la soumission d'une demande déclenche (1) la copie interne vers
 * {@code contact@onesley.com} (toujours) et (2) l'accusé de réception au gérant (si email présent),
 * avec le bon template / destinataire / branding — sans toucher Resend (mocké). Les champs
 * {@code @Value} sont injectés via {@link ReflectionTestUtils} (pas de contexte Spring).
 */
@ExtendWith(MockitoExtension.class)
class StoreOnboardingRequestEmailListenerTest {

    @Mock EmailTemplateService templateService;
    @Mock ResendClient resendClient;
    @InjectMocks StoreOnboardingRequestEmailListener listener;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "internalRecipient", "contact@onesley.com");
        ReflectionTestUtils.setField(listener, "adminBaseUrl", "https://admin.test");
    }

    private StoreOnboardingRequestedEvent event(String ownerEmail) {
        return new StoreOnboardingRequestedEvent(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "Le Bistrot", "Ada Lovelace", ownerEmail, "Ada", "Casablanca",
            List.of(UUID.randomUUID()), Instant.now());
    }

    @Test
    void requested_sendsInternalCopy_andApplicantAck() {
        listener.onStoreOnboardingRequested(event("gerant@bistrot.ma"));

        // Les 2 templates sont rendus avec le branding oneclick.
        verify(templateService).render(eq("oneclick"), eq("store-onboarding-request-internal"), any(), anyString());
        verify(templateService).render(eq("oneclick"), eq("store-onboarding-request-received"), any(), anyString());

        ArgumentCaptor<EmailSendDto> dtoCap = ArgumentCaptor.forClass(EmailSendDto.class);
        verify(resendClient, times(2)).send(dtoCap.capture(), any());

        EmailSendDto internal = dtoCap.getAllValues().stream()
            .filter(d -> d.template().equals("store-onboarding-request-internal")).findFirst().orElseThrow();
        assertThat(internal.to()).containsExactly("contact@onesley.com");
        assertThat(internal.subjectFr()).contains("Le Bistrot").contains("Casablanca");
        assertThat(internal.variables()).containsEntry("businessName", "Le Bistrot");
        assertThat(String.valueOf(internal.variables().get("adminLink")))
            .contains("/forge/demandes-inscription?highlight=11111111-1111-1111-1111-111111111111");

        EmailSendDto ack = dtoCap.getAllValues().stream()
            .filter(d -> d.template().equals("store-onboarding-request-received")).findFirst().orElseThrow();
        assertThat(ack.to()).containsExactly("gerant@bistrot.ma");
        assertThat(ack.variables()).containsEntry("firstName", "Ada");
    }

    @Test
    void requested_noOwnerEmail_sendsOnlyInternalCopy() {
        listener.onStoreOnboardingRequested(event("   "));

        verify(templateService).render(eq("oneclick"), eq("store-onboarding-request-internal"), any(), anyString());
        verify(templateService, never()).render(eq("oneclick"), eq("store-onboarding-request-received"), any(), anyString());

        ArgumentCaptor<EmailSendDto> dtoCap = ArgumentCaptor.forClass(EmailSendDto.class);
        verify(resendClient, times(1)).send(dtoCap.capture(), any());
        assertThat(dtoCap.getValue().to()).containsExactly("contact@onesley.com");
    }
}
