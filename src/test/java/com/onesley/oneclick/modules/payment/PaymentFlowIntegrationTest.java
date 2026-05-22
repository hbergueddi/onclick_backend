package com.onesley.oneclick.modules.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/payments} : methods + payments + refunds + transactions. */
class PaymentFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void payment_fullFlow() throws Exception {
        String admin = adminBearer();
        String uid = userId();

        // methods
        assertThat(restTemplate.exchange(url("/api/payments/methods/by-user/" + uid), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> mPost = restTemplate.exchange(url("/api/payments/methods"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", uid, "type", "card", "last4", "4242"), admin), String.class);
        assertThat(mPost.getStatusCode().is2xxSuccessful()).isTrue();
        String methodId = om.readTree(mPost.getBody()).get("id").asText();

        // payments
        assertThat(restTemplate.exchange(url("/api/payments?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> pPost = restTemplate.exchange(url("/api/payments"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", uid, "amount", 100.0, "currency", "MAD"), admin), String.class);
        assertThat(pPost.getStatusCode().is2xxSuccessful()).isTrue();
        String paymentId = om.readTree(pPost.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/payments/" + paymentId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/payments/" + paymentId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "succeeded"), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // refunds
        assertThat(restTemplate.exchange(url("/api/payments/" + paymentId + "/refunds"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> rPost = restTemplate.exchange(url("/api/payments/refunds"), HttpMethod.POST,
            jsonJwtEntity(Map.of("paymentId", paymentId, "amount", 10.0, "reason", "L4 refund"), admin), String.class);
        assertThat(rPost.getStatusCode().is2xxSuccessful()).isTrue();
        String refundId = om.readTree(rPost.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/payments/refunds/" + refundId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "succeeded"), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // transactions read
        assertThat(restTemplate.exchange(url("/api/payments/" + paymentId + "/transactions"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // self-clean method
        assertThat(restTemplate.exchange(url("/api/payments/methods/" + methodId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void payment_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/payments/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createPayment_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/payments"), HttpMethod.POST,
            jsonJwtEntity(Map.of("currency", "MAD"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void payments_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/payments?page=0&size=5"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
