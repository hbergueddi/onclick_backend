package com.onesley.oneclick.core.ai;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « profondeur » — {@code /api/ai} : sans clé Groq en test, le client tombe en
 * mode stub → les endpoints répondent 200 avec une réponse factice (le corps du
 * contrôleur + GroqClient.chat (branche stub) sont bien exécutés).
 */
class AiFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void careChat_stubMode_200() {
        var r = restTemplate.exchange(url("/api/ai/care-chat"), HttpMethod.POST,
            jsonJwtEntity(Map.of("message", "Bonjour, un souci de facture"), adminBearer()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("message");
    }

    @Test
    void assistant_stubMode_200() {
        var r = restTemplate.exchange(url("/api/ai/assistant"), HttpMethod.POST,
            jsonJwtEntity(Map.of("prompt", "Résume mes ventes"), adminBearer()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void eliteReview_stubMode_200() {
        var r = restTemplate.exchange(url("/api/ai/elite-review"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", "r1",
                "reviews", List.of(Map.of("text", "Excellent", "rating", 5))), adminBearer()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void plan_stubMode_200() {
        var r = restTemplate.exchange(url("/api/ai/plan"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", "r1", "weekStart", "2026-05-25"), adminBearer()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void careChatStream_stubMode_2xx() {
        var r = restTemplate.exchange(url("/api/ai/care-chat/stream"), HttpMethod.POST,
            jsonJwtEntity(Map.of("message", "Bonjour"), adminBearer()), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void assistantStream_stubMode_2xx() {
        var r = restTemplate.exchange(url("/api/ai/assistant/stream"), HttpMethod.POST,
            jsonJwtEntity(Map.of("prompt", "Résume"), adminBearer()), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void careChat_invalidBody_400() {
        var r = restTemplate.exchange(url("/api/ai/care-chat"), HttpMethod.POST,
            jsonJwtEntity(Map.of("context", Map.of()), adminBearer()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
