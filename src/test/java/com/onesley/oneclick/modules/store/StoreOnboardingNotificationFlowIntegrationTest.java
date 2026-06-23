package com.onesley.oneclick.modules.store;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendDto;
import com.onesley.oneclick.core.email.api.EmailDtos.EmailSendResultDto;
import com.onesley.oneclick.core.email.internal.ResendClient;
import com.onesley.oneclick.shared.events.StoreOnboardingDecidedEvent;
import com.onesley.oneclick.shared.events.StoreOnboardingRequestedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Intégration event-driven (contexte Spring complet) des Lots B4 / B5 onboarding d'enseigne.
 * La publication réelle d'un event via {@link ApplicationEventPublisher} dans une transaction
 * committée (les listeners {@code @ApplicationModuleListener} sont after-commit + async) provoque :
 *
 * <ul>
 *   <li><b>B4</b> — {@link StoreOnboardingRequestedEvent} → ligne {@code notifications} de type
 *       {@code system} persistée pour chaque admin destinataire porté sur l'event ;</li>
 *   <li><b>B5</b> — {@link StoreOnboardingDecidedEvent} → {@code ResendClient.send} invoqué
 *       (mocké) avec le bon template ({@code approved}/{@code rejected}) et le bon destinataire.</li>
 * </ul>
 *
 * <p>Resend est <b>mocké</b> ({@link MockitoBean}) — aucun appel HTTP externe. Le destinataire admin
 * B4 = le SUPERADMIN seedé (FK {@code notifications.recipient_user_id → users} + garde-fou
 * {@code NotificationRecipientGuard} exigent un compte existant).</p>
 */
class StoreOnboardingNotificationFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired ApplicationEventPublisher events;

    /** Resend mocké : on vérifie l'invocation du canal email B5 sans envoi réel. */
    @MockitoBean ResendClient resendClient;

    private void publishCommitted(Object event) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> events.publishEvent(event));
    }

    private long countSystemNotifsSince(UUID recipient, Instant since) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM notifications WHERE recipient_user_id = ?::uuid AND type = 'system' "
            + "AND created_at >= ?",
            Long.class, recipient.toString(), java.sql.Timestamp.from(since));
        return n == null ? 0 : n;
    }

    private boolean awaitSystemNotif(UUID recipient, Instant since) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (countSystemNotifsSince(recipient, since) >= 1) return true;
            Thread.sleep(200);
        }
        return false;
    }

    // ── B4 — demande créée → notif in-app admin (type system) ──────────────────────────────────
    @Test
    void onboardingRequested_persistsSystemNotifForAdmin() throws Exception {
        Instant since = Instant.now();
        try {
            publishCommitted(new StoreOnboardingRequestedEvent(
                UUID.randomUUID(), "Le Bistrot", "Ada Lovelace",
                "owner-it@bistrot.ma", "Ada", "Casablanca",
                List.of(SEED_SUPERADMIN_ID), Instant.now()));

            assertThat(awaitSystemNotif(SEED_SUPERADMIN_ID, since))
                .as("B4 : notif system persistée pour l'admin").isTrue();
        } finally {
            jdbc.update("DELETE FROM notifications WHERE recipient_user_id = ?::uuid AND type = 'system' "
                + "AND created_at >= ?", SEED_SUPERADMIN_ID.toString(), java.sql.Timestamp.from(since));
        }
    }

    // ── B5 — décision approuvée → email branded (Resend mocké) ─────────────────────────────────
    @Test
    void onboardingDecided_approved_invokesResendWithApprovedTemplate() {
        when(resendClient.send(any(EmailSendDto.class), anyString()))
            .thenReturn(new EmailSendResultDto(true, 1, "msg-it", null));

        publishCommitted(new StoreOnboardingDecidedEvent(
            UUID.randomUUID(), "gerant-it@bistrot.ma", "Ada Lovelace", "Le Bistrot",
            true, null, "https://app.test/login", "TempPass2345", Instant.now()));

        ArgumentCaptor<EmailSendDto> cap = ArgumentCaptor.forClass(EmailSendDto.class);
        // Listener async after-commit → bounded timeout sur l'invocation Resend.
        verify(resendClient, timeout(10_000).atLeastOnce()).send(cap.capture(), anyString());
        assertThat(cap.getAllValues()).anySatisfy(dto -> {
            assertThat(dto.template()).isEqualTo("store-onboarding-decision-approved");
            assertThat(dto.tenantSlug()).isEqualTo("oneclick");
            assertThat(dto.to()).containsExactly("gerant-it@bistrot.ma");
        });
    }

    @Test
    void onboardingDecided_rejected_invokesResendWithRejectedTemplate() {
        when(resendClient.send(any(EmailSendDto.class), anyString()))
            .thenReturn(new EmailSendResultDto(true, 1, "msg-it2", null));

        publishCommitted(new StoreOnboardingDecidedEvent(
            UUID.randomUUID(), "gerant-it2@bistrot.ma", "Ada Lovelace", "Le Bistrot",
            false, "Dossier incomplet", null, null, Instant.now()));

        ArgumentCaptor<EmailSendDto> cap = ArgumentCaptor.forClass(EmailSendDto.class);
        verify(resendClient, timeout(10_000).atLeastOnce()).send(cap.capture(), anyString());
        assertThat(cap.getAllValues()).anySatisfy(dto -> {
            assertThat(dto.template()).isEqualTo("store-onboarding-decision-rejected");
            assertThat(dto.to()).containsExactly("gerant-it2@bistrot.ma");
        });
    }
}
