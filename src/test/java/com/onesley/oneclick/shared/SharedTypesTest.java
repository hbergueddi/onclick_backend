package com.onesley.oneclick.shared;

import com.onesley.oneclick.exception.ApiException;
import com.onesley.oneclick.exception.UnauthorizedException;
import com.onesley.oneclick.exception.UnprocessableException;
import com.onesley.oneclick.shared.events.PaymentSucceededEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests unitaires des types partagés peu/non couverts : event + exceptions. */
class SharedTypesTest {

    @Test
    void paymentSucceededEvent_accessors() {
        UUID paymentId = UUID.randomUUID(), userId = UUID.randomUUID();
        Instant now = Instant.now();
        var e = new PaymentSucceededEvent(paymentId, userId, UUID.randomUUID(),
            new BigDecimal("100"), "MAD", "stripe", "reservation", UUID.randomUUID(), now);
        assertThat(e.paymentId()).isEqualTo(paymentId);
        assertThat(e.userId()).isEqualTo(userId);
        assertThat(e.amount()).isEqualByComparingTo("100");
        assertThat(e.currency()).isEqualTo("MAD");
        assertThat(e.occurredAt()).isEqualTo(now);
        assertThat(e).isEqualTo(new PaymentSucceededEvent(paymentId, userId, e.tenantId(),
            new BigDecimal("100"), "MAD", "stripe", "reservation", e.referenceId(), now));
    }

    @Test
    void apiExceptions_statusAndMessage() {
        UnauthorizedException u = new UnauthorizedException("non authentifié");
        assertThat(u.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(u.getMessage()).isEqualTo("non authentifié");
        assertThat(u).isInstanceOf(ApiException.class);

        UnprocessableException p = new UnprocessableException("règle métier violée");
        assertThat(p.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(p.getMessage()).isEqualTo("règle métier violée");

        // constructeur avec cause (couvre la surcharge ApiException)
        ApiException withCause = new ApiException(HttpStatus.CONFLICT, "conflit", new RuntimeException("root"));
        assertThat(withCause.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(withCause.getCause()).isInstanceOf(RuntimeException.class);
    }
}
