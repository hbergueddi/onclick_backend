package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendResultDto;
import com.onesley.oneclick.shared.events.MemberEnrollmentInvitedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link MemberEnrollmentInviteEmailListener} (Gap #10, core.email).
 *
 * <p>Le listener est exercé hors du chemin d'intégration JDBC (qui seede l'invitation
 * directement) : ces tests isolés vérifient la composition du lien magique
 * {@code /accept-invite?token=…} (URL-encodé), le branding tenant (+ fallback défaut)
 * et l'appel {@code ResendClient.send} avec le bon template/destinataire.</p>
 */
@ExtendWith(MockitoExtension.class)
class MemberEnrollmentInviteEmailListenerTest {

    @Mock EmailTemplateService templateService;
    @Mock ResendClient resendClient;
    @InjectMocks MemberEnrollmentInviteEmailListener listener;

    private MemberEnrollmentInvitedEvent event(String slug, String name) {
        return new MemberEnrollmentInvitedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "membre@x.ma", "Yasmine",
            slug, name, "raw token+/=", Instant.parse("2026-06-14T10:00:00Z"), Instant.now());
    }

    @Test
    void onMemberEnrollmentInvited_composesMagicLink_andSends() {
        ReflectionTestUtils.setField(listener, "frontendBaseUrl", "https://app.test");
        when(templateService.render(eq("palmeraie"), eq("member-enrollment-invite"), any(), anyString()))
            .thenReturn("<html>ok</html>");
        when(resendClient.send(any(), eq("<html>ok</html>")))
            .thenReturn(new EmailSendResultDto(true, 1, "msg-1", null));

        listener.onMemberEnrollmentInvited(event("palmeraie", "Palmeraie Country Club"));

        // Variables du template : lien magique URL-encodé vers /accept-invite.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCap = ArgumentCaptor.forClass(Map.class);
        verify(templateService).render(eq("palmeraie"), eq("member-enrollment-invite"), varsCap.capture(), anyString());
        String link = String.valueOf(varsCap.getValue().get("inviteLink"));
        assertThat(link).startsWith("https://app.test/accept-invite?token=");
        // Token URL-encodé : "+" → %2B, "/" → %2F, "=" → %3D (sinon casserait le query param).
        assertThat(link).contains("%2B").contains("%2F").contains("%3D");
        assertThat(varsCap.getValue()).containsEntry("tenantName", "Palmeraie Country Club");
        assertThat(varsCap.getValue()).containsEntry("firstName", "Yasmine");

        // Envoi via ResendClient avec le bon template + destinataire.
        ArgumentCaptor<EmailSendDto> dtoCap = ArgumentCaptor.forClass(EmailSendDto.class);
        verify(resendClient).send(dtoCap.capture(), eq("<html>ok</html>"));
        assertThat(dtoCap.getValue().template()).isEqualTo("member-enrollment-invite");
        assertThat(dtoCap.getValue().tenantSlug()).isEqualTo("palmeraie");
        assertThat(dtoCap.getValue().to()).containsExactly("membre@x.ma");
    }

    @Test
    void onMemberEnrollmentInvited_nullTenant_fallsBackToDefaultBranding() {
        ReflectionTestUtils.setField(listener, "frontendBaseUrl", "https://app.test");
        when(templateService.render(eq("default"), eq("member-enrollment-invite"), any(), anyString()))
            .thenReturn("<html/>");
        when(resendClient.send(any(), anyString()))
            .thenReturn(new EmailSendResultDto(true, 1, "id", null));

        listener.onMemberEnrollmentInvited(event(null, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCap = ArgumentCaptor.forClass(Map.class);
        verify(templateService).render(eq("default"), eq("member-enrollment-invite"), varsCap.capture(), anyString());
        assertThat(varsCap.getValue()).containsEntry("tenantName", "OneClick");
    }
}
