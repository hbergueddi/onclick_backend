package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushReservationDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link FcmPushService} (L3 — core.notification).
 * Mode stub (FCM non configuré) vs fan-out (configuré), promo + reservation.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class FcmPushServiceTest {

    @Mock DeviceTokenRepository tokenRepo;
    @Mock NotificationRepository notifRepo;

    private FcmPushService svc(boolean configured) {
        return configured
            ? new FcmPushService("oneclick-129bb", "{\"client_email\":\"x\"}", tokenRepo, notifRepo)
            : new FcmPushService("", "", tokenRepo, notifRepo);
    }
    private DeviceToken token() {
        return new DeviceToken(UUID.randomUUID(), UUID.randomUUID(), "tok", "ios");
    }

    @Test
    void isConfigured_trueWhenSet_falseWhenBlank() {
        assertThat(svc(true).isConfigured()).isTrue();
        assertThat(svc(false).isConfigured()).isFalse();
    }

    @Test
    void sendPromo_notConfigured_stub() {
        lenient().when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token()));
        var res = svc(false).sendPromo(new PushPromoDto(UUID.randomUUID(), List.of(UUID.randomUUID()), "T", "B", "/l"));
        assertThat(res.sent()).isZero();
        assertThat(res.message()).isEqualTo("FCM not configured");
    }

    @Test
    void sendPromo_configured_fanOut() {
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token()));
        var res = svc(true).sendPromo(new PushPromoDto(UUID.randomUUID(),
            List.of(UUID.randomUUID(), UUID.randomUUID()), "T", "B", "/l"));
        assertThat(res.total()).isEqualTo(2);   // 1 token par user × 2 users
        assertThat(res.sent()).isEqualTo(2);
        assertThat(res.message()).contains("stub");
    }

    @Test
    void sendReservation_notConfigured_stub() {
        lenient().when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token()));
        var res = svc(false).sendReservation(new PushReservationDto(
            UUID.randomUUID(), UUID.randomUUID(), "confirmed", "T", "B", "/l"));
        assertThat(res.message()).isEqualTo("FCM not configured");
    }

    @Test
    void sendReservation_configured_fanOut() {
        when(tokenRepo.findAllByUserId(any())).thenReturn(List.of(token(), token()));
        var res = svc(true).sendReservation(new PushReservationDto(
            UUID.randomUUID(), UUID.randomUUID(), "confirmed", "T", "B", null));
        assertThat(res.sent()).isEqualTo(2);
    }
}
