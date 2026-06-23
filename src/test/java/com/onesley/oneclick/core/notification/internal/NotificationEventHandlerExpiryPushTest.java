package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.shared.events.ContractExpiringSoonEvent;
import com.onesley.oneclick.shared.events.PointsExpiringSoonEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires des alertes pré-expiration (Features A/B) côté {@link NotificationEventHandler} :
 * points fidélité (→ client : in-app metadata + push) et contrats (→ chaque admin : in-app + push).
 * Parité legacy {@code notify-expiring-points} / {@code notify-expiring-contracts}.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerExpiryPushTest {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @InjectMocks NotificationEventHandler handler;
    @Captor ArgumentCaptor<PushPromoDto> pushCaptor;

    @Test
    void pointsExpiring_createsInAppWithMetadata_andPushesClient() {
        UUID client = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        handler.onPointsExpiringSoon(new PointsExpiringSoonEvent(client, account, 120, "12/06", "j7", Instant.now()));
        // in-app avec metadata (accountId/milestone pour l'anti-doublon du cron)
        verify(notificationService).createPointsExpiringAlert(
            eq(client), anyString(), anyString(), anyString(), eq(account), eq("j7"));
        // push au client
        verify(pushService).sendPromo(pushCaptor.capture());
        assertThat(pushCaptor.getValue().userIds()).containsExactly(client);
    }

    @Test
    void contractExpiring_perAdmin_createsInApp_andPush() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID contract = UUID.randomUUID();
        handler.onContractExpiringSoon(new ContractExpiringSoonEvent(
            contract, UUID.randomUUID(), "OCHI-001", "Resto X", "12/06/2026", "j30", List.of(a1, a2), Instant.now()));
        // 1 notif in-app + 1 push par admin (2 admins)
        verify(notificationService, times(2)).createContractExpiringAlert(
            any(), anyString(), anyString(), anyString(), eq(contract), eq("j30"));
        verify(pushService, times(2)).sendPromo(any());
    }

    @Test
    void contractExpiring_noRecipients_noop() {
        handler.onContractExpiringSoon(new ContractExpiringSoonEvent(
            UUID.randomUUID(), null, "X", null, "12/06/2026", "j7", List.of(), Instant.now()));
        verify(notificationService, never()).createContractExpiringAlert(
            any(), anyString(), anyString(), anyString(), any(), anyString());
        verify(pushService, never()).sendPromo(any());
    }
}
