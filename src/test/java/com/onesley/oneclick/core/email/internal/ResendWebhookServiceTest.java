package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Tests unitaires de {@link ResendWebhookService} (Gap #4 — parsing + signature Svix). */
@ExtendWith(MockitoExtension.class)
class ResendWebhookServiceTest {

    @Mock EmailBounceService bounceService;

    private ResendWebhookService service(String secret) {
        ResendWebhookService s = new ResendWebhookService(bounceService);
        ReflectionTestUtils.setField(s, "webhookSecret", secret);
        return s;
    }

    @Test
    void handle_bouncedPermanent_records() {
        String body = "{\"type\":\"email.bounced\",\"data\":{\"to\":[\"x@y.ma\"],\"bounce\":{\"type\":\"Permanent\"}}}";
        int n = service("").handle(body, null, null, null); // no secret → skip verify
        assertThat(n).isEqualTo(1);
        verify(bounceService).recordBounce(eq("x@y.ma"), eq("permanent"), org.mockito.ArgumentMatchers.any(),
            eq("resend-webhook"), eq(body));
    }

    @Test
    void handle_bouncedTransient_records() {
        String body = "{\"type\":\"email.bounced\",\"data\":{\"to\":[\"x@y.ma\"],\"bounce\":{\"type\":\"Transient\"}}}";
        service("").handle(body, null, null, null);
        verify(bounceService).recordBounce(eq("x@y.ma"), eq("transient"), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handle_complained_records() {
        String body = "{\"type\":\"email.complained\",\"data\":{\"to\":[\"z@y.ma\"]}}";
        service("").handle(body, null, null, null);
        verify(bounceService).recordBounce(eq("z@y.ma"), eq("complaint"), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handle_delivered_noop() {
        int n = service("").handle("{\"type\":\"email.delivered\",\"data\":{\"to\":[\"x@y.ma\"]}}", null, null, null);
        assertThat(n).isZero();
        verify(bounceService, never()).recordBounce(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handle_secretSet_missingHeaders_forbidden() {
        assertThatThrownBy(() -> service("whsec_" + Base64.getEncoder().encodeToString("k".getBytes()))
            .handle("{}", null, null, null))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void handle_secretSet_wrongSignature_forbidden() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("k".getBytes());
        assertThatThrownBy(() -> service(secret).handle("{}", "id1", "1700000000", "v1,deadbeef"))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void handle_secretSet_validSignature_ok() throws Exception {
        byte[] rawKey = "supersecretkey".getBytes(StandardCharsets.UTF_8);
        String secret = "whsec_" + Base64.getEncoder().encodeToString(rawKey);
        String body = "{\"type\":\"email.bounced\",\"data\":{\"to\":[\"ok@y.ma\"],\"bounce\":{\"type\":\"Permanent\"}}}";
        String id = "msg_1", ts = "1700000000";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(rawKey, "HmacSHA256"));
        String sig = Base64.getEncoder().encodeToString(
            mac.doFinal((id + "." + ts + "." + body).getBytes(StandardCharsets.UTF_8)));

        int n = service(secret).handle(body, id, ts, "v1," + sig);

        assertThat(n).isEqualTo(1);
        verify(bounceService).recordBounce(eq("ok@y.ma"), eq("permanent"),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq(body));
    }
}
