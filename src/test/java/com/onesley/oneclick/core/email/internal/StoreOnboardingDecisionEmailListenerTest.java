package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendResultDto;
import com.onesley.oneclick.shared.events.StoreOnboardingDecidedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés de {@link StoreOnboardingDecisionEmailListener} (Lot B5, core.email).
 *
 * <p>Vérifie le choix de template selon le verdict (approved/rejected), la composition des
 * variables (lien de connexion si approuvé, motif si refusé), le destinataire et le branding
 * {@code oneclick} — sans toucher Resend (mocké).</p>
 */
@ExtendWith(MockitoExtension.class)
class StoreOnboardingDecisionEmailListenerTest {

    @Mock EmailTemplateService templateService;
    @Mock ResendClient resendClient;
    @InjectMocks StoreOnboardingDecisionEmailListener listener;

    private StoreOnboardingDecidedEvent approved() {
        return new StoreOnboardingDecidedEvent(
            UUID.randomUUID(), "gerant@bistrot.ma", "Ada Lovelace", "Le Bistrot",
            true, null, "https://app.test/login", "TempPass2345", Instant.now());
    }

    private StoreOnboardingDecidedEvent rejected(String reason) {
        return new StoreOnboardingDecidedEvent(
            UUID.randomUUID(), "gerant@bistrot.ma", "Ada Lovelace", "Le Bistrot",
            false, reason, null, null, Instant.now());
    }

    @Test
    void approved_usesApprovedTemplate_withLoginLink_andSends() {
        when(templateService.render(eq("oneclick"), eq("store-onboarding-decision-approved"), any(), anyString()))
            .thenReturn("<html>ok</html>");
        when(resendClient.send(any(), eq("<html>ok</html>")))
            .thenReturn(new EmailSendResultDto(true, 1, "msg-1", null));

        listener.onStoreOnboardingDecided(approved());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCap = ArgumentCaptor.forClass(Map.class);
        verify(templateService).render(eq("oneclick"), eq("store-onboarding-decision-approved"),
            varsCap.capture(), anyString());
        assertThat(varsCap.getValue()).containsEntry("businessName", "Le Bistrot");
        assertThat(varsCap.getValue()).containsEntry("contactName", "Ada Lovelace");
        assertThat(varsCap.getValue()).containsEntry("loginLink", "https://app.test/login");
        // BE-2 — l'email d'approbation expose les identifiants temporaires.
        assertThat(varsCap.getValue()).containsEntry("loginEmail", "gerant@bistrot.ma");
        assertThat(varsCap.getValue()).containsEntry("tempPassword", "TempPass2345");

        ArgumentCaptor<EmailSendDto> dtoCap = ArgumentCaptor.forClass(EmailSendDto.class);
        verify(resendClient).send(dtoCap.capture(), eq("<html>ok</html>"));
        assertThat(dtoCap.getValue().template()).isEqualTo("store-onboarding-decision-approved");
        assertThat(dtoCap.getValue().tenantSlug()).isEqualTo("oneclick");
        assertThat(dtoCap.getValue().to()).containsExactly("gerant@bistrot.ma");
        assertThat(dtoCap.getValue().subjectFr()).contains("approuvée");
    }

    @Test
    void rejected_usesRejectedTemplate_withReason() {
        when(templateService.render(eq("oneclick"), eq("store-onboarding-decision-rejected"), any(), anyString()))
            .thenReturn("<html/>");
        when(resendClient.send(any(), anyString()))
            .thenReturn(new EmailSendResultDto(true, 1, "id", null));

        listener.onStoreOnboardingDecided(rejected("Dossier incomplet"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCap = ArgumentCaptor.forClass(Map.class);
        verify(templateService).render(eq("oneclick"), eq("store-onboarding-decision-rejected"),
            varsCap.capture(), anyString());
        assertThat(varsCap.getValue()).containsEntry("rejectionReason", "Dossier incomplet");
        assertThat(varsCap.getValue()).doesNotContainKey("loginLink");
    }

    @Test
    void rejected_blankReason_fallsBackToGenericMotif() {
        when(templateService.render(eq("oneclick"), eq("store-onboarding-decision-rejected"), any(), anyString()))
            .thenReturn("<html/>");
        when(resendClient.send(any(), anyString()))
            .thenReturn(new EmailSendResultDto(true, 1, "id", null));

        listener.onStoreOnboardingDecided(rejected("   "));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCap = ArgumentCaptor.forClass(Map.class);
        verify(templateService).render(eq("oneclick"), eq("store-onboarding-decision-rejected"),
            varsCap.capture(), anyString());
        assertThat(String.valueOf(varsCap.getValue().get("rejectionReason"))).isNotBlank();
    }

    @Test
    void noRecipientEmail_skipsSend() {
        listener.onStoreOnboardingDecided(new StoreOnboardingDecidedEvent(
            UUID.randomUUID(), "  ", "Ada", "Le Bistrot", true, null, "https://app.test/login", "TempPass2345", Instant.now()));

        verify(resendClient, never()).send(any(), anyString());
        verify(templateService, never()).render(anyString(), anyString(), any(), anyString());
    }
}
