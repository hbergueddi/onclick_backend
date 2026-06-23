package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendResultDto;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.core.tenant.api.TenantDirectoryApi;
import com.onesley.oneclick.shared.events.FeedbackCreatedEvent;
import com.onesley.oneclick.shared.events.FeedbackRepliedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du canal email feedback (gap #6) : résolution des emails (UserDirectoryApi),
 * branding par slug (TenantDirectoryApi), envoi via ResendClient (mocké, stub-safe en réel).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackEmailListenerTest {

    @Mock UserDirectoryApi userDirectory;
    @Mock TenantDirectoryApi tenantDirectory;
    @Mock ResendClient resendClient;
    @InjectMocks FeedbackEmailListener listener;
    @Captor ArgumentCaptor<EmailSendDto> dtoCaptor;

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(listener, "frontendBaseUrl", "https://app-oneclick.net");
        lenient().when(tenantDirectory.slugById(tenant)).thenReturn(Optional.of("palmeraie"));
        lenient().when(resendClient.send(any(), any())).thenReturn(new EmailSendResultDto(true, 1, "msg", null));
    }

    private UserName named(UUID id, String email) {
        return new UserName(id, "Prénom", "Nom", "+212600000000", email, null);
    }

    @Test
    void created_resolvesRecipientEmails_andSendsBranded() {
        UUID o1 = UUID.randomUUID(), o2 = UUID.randomUUID();
        when(userDirectory.nameById(o1)).thenReturn(Optional.of(named(o1, "adil@pcc.ma")));
        when(userDirectory.nameById(o2)).thenReturn(Optional.of(named(o2, "owner@pcc.ma")));

        listener.onFeedbackCreated(new FeedbackCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), tenant, UUID.randomUUID(),
            List.of(o1, o2), "unhappy", "service", Instant.now()));

        verify(resendClient).send(dtoCaptor.capture(), any());
        EmailSendDto dto = dtoCaptor.getValue();
        assertThat(dto.to()).containsExactlyInAnyOrder("adil@pcc.ma", "owner@pcc.ma");
        assertThat(dto.tenantSlug()).isEqualTo("palmeraie");
    }

    @Test
    void created_noRecipients_noEmail() {
        listener.onFeedbackCreated(new FeedbackCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), tenant, UUID.randomUUID(),
            List.of(), "happy", null, Instant.now()));
        verify(resendClient, never()).send(any(), any());
    }

    @Test
    void created_recipientsWithoutResolvableEmail_noEmail() {
        UUID o1 = UUID.randomUUID();
        when(userDirectory.nameById(o1)).thenReturn(Optional.empty());
        listener.onFeedbackCreated(new FeedbackCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), tenant, UUID.randomUUID(),
            List.of(o1), "happy", null, Instant.now()));
        verify(resendClient, never()).send(any(), any());
    }

    @Test
    void replied_sendsToMember() {
        UUID member = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        when(userDirectory.nameById(member)).thenReturn(Optional.of(named(member, "membre@pcc.ma")));

        listener.onFeedbackReplied(new FeedbackRepliedEvent(
            feedbackId, member, tenant, UUID.randomUUID(), "happy", Instant.now()));

        verify(resendClient).send(dtoCaptor.capture(), any());
        EmailSendDto dto = dtoCaptor.getValue();
        assertThat(dto.to()).containsExactly("membre@pcc.ma");
        assertThat(dto.tenantSlug()).isEqualTo("palmeraie");
        assertThat(dto.subjectFr()).contains("Réponse à votre avis");
    }
}
