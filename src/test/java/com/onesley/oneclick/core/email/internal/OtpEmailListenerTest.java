package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendResultDto;
import com.onesley.oneclick.shared.events.OtpRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link OtpEmailListener} (P1 enrollment — livraison OTP par email).
 * Vérifie le filtrage par canal (purposes email envoyés, purposes non-email ignorés) et le
 * contenu de l'email (code en clair, branding tenant, destinataire).
 */
@ExtendWith(MockitoExtension.class)
class OtpEmailListenerTest {

    @Mock EmailTemplateService templateService;
    @Mock ResendClient resendClient;

    private OtpEmailListener listener() {
        return new OtpEmailListener(templateService, resendClient);
    }

    private OtpRequestedEvent event(String purpose) {
        return new OtpRequestedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "ada@x.ma", "Ada",
            purpose, "123456", "palmeraie", "Palmeraie Country Club",
            Instant.now().plusSeconds(600), Instant.now());
    }

    @Test
    void signupPurpose_sendsBrandedEmailWithCode() {
        when(templateService.render(eq("palmeraie"), eq("otp-code"), any(), anyString())).thenReturn("<html>code</html>");
        when(resendClient.send(any(EmailSendDto.class), anyString()))
            .thenReturn(new EmailSendResultDto(true, 1, "msg-1", null));

        listener().onOtpRequested(event("signup"));

        ArgumentCaptor<EmailSendDto> dto = ArgumentCaptor.forClass(EmailSendDto.class);
        verify(resendClient).send(dto.capture(), eq("<html>code</html>"));
        assertThat(dto.getValue().to()).containsExactly("ada@x.ma");
        assertThat(dto.getValue().tenantSlug()).isEqualTo("palmeraie");   // branding tenant
        assertThat(dto.getValue().template()).isEqualTo("otp-code");
        assertThat(dto.getValue().subjectEn()).contains("123456");        // code en clair (fallback texte)
    }

    @Test
    void resetPasswordPurpose_isEmailDelivered() {
        when(templateService.render(anyString(), anyString(), any(), anyString())).thenReturn("<html/>");
        when(resendClient.send(any(EmailSendDto.class), anyString()))
            .thenReturn(new EmailSendResultDto(true, 1, "msg-2", null));

        listener().onOtpRequested(event("reset_password"));

        verify(resendClient).send(any(EmailSendDto.class), anyString());
    }

    @Test
    void redemptionPurpose_isSkipped_noEmail() {
        listener().onOtpRequested(event("redemption"));   // canal staff/in-app, jamais emailé
        verify(resendClient, never()).send(any(), anyString());
        verify(templateService, never()).render(anyString(), anyString(), any(), anyString());
    }

    @Test
    void verifyPhonePurpose_isSkipped_noEmail() {
        listener().onOtpRequested(event("verify_phone"));  // canal SMS (non implémenté ici)
        verify(resendClient, never()).send(any(), anyString());
    }

    @Test
    void nullPurpose_isSkipped_noEmail() {
        listener().onOtpRequested(event(null));
        verify(resendClient, never()).send(any(), anyString());
    }
}
