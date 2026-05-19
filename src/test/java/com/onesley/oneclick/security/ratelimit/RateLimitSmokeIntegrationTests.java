package com.onesley.oneclick.security.ratelimit;

import com.onesley.oneclick.AbstractIntegrationTest;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Bug 35 — rate limiting distribué Bucket4j-Redis.
 *
 * <p>Boote avec {@code app.rate-limit.enabled=true} + capacité défaut = 3 pour
 * déclencher un 429 rapidement. Exerce le wiring complet
 * Lettuce → Redis → {@code LettuceBasedProxyManager} → {@link RateLimitFilter}
 * — chemin NON couvert par les autres smoke tests (qui tournent rate-limit
 * désactivé).
 *
 * <p>Le {@code @TestPropertySource} de cette sous-classe est mergé avec celui
 * d'{@link AbstractIntegrationTest} (oauth2 + jwt-secret), la sous-classe gagne
 * sur les clés en conflit. Ça crée un contexte Spring distinct (les beans
 * Bucket4j conditionnels {@code app.rate-limit.enabled=true} ne sont instanciés
 * que dans ce contexte).
 *
 * <p>{@code @BeforeEach} flush les clés {@code rate-limit:*} de Redis pour
 * isoler chaque test (l'état bucket est distribué/persistant, contrairement à
 * l'ancien ConcurrentHashMap en mémoire).
 */
@TestPropertySource(properties = {
    "app.rate-limit.enabled=true",
    "app.rate-limit.defaults.capacity=3",
    "app.rate-limit.defaults.refill-period=PT1M",
})
class RateLimitSmokeIntegrationTests extends AbstractIntegrationTest {

    /** Path authentifié NON listé dans {@code endpoints} → tombe sur les defaults (capacity=3). */
    private static final String UNLISTED_PATH = "/api/users/me/permissions";

    @Autowired
    StatefulRedisConnection<String, byte[]> rateLimitConnection;

    @BeforeEach
    void flushRateLimitKeys() {
        List<String> keys = rateLimitConnection.sync().keys("rate-limit:*");
        if (!keys.isEmpty()) {
            rateLimitConnection.sync().del(keys.toArray(new String[0]));
        }
    }

    @Test
    void defaultBucket_blocksAfterCapacityExceeded() {
        // 3 premières requêtes : sous la limite (200 car adminBearer authentifié).
        for (int i = 1; i <= 3; i++) {
            ResponseEntity<String> ok = restTemplate.exchange(
                url(UNLISTED_PATH), HttpMethod.GET, jwtEntity(adminBearer()), String.class);
            assertThat(ok.getStatusCode())
                .as("requête %d doit passer sous la limite (capacity=3)", i)
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
        // 4ème : bucket vide → 429 (le filter tourne AVANT l'auth, donc 429 prime).
        ResponseEntity<String> blocked = restTemplate.exchange(
            url(UNLISTED_PATH), HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getHeaders().getFirst("Retry-After"))
            .as("header Retry-After RFC 7807").isNotNull();
        assertThat(blocked.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void allowedRequest_exposesRateLimitHeaders() {
        ResponseEntity<String> resp = restTemplate.exchange(
            url(UNLISTED_PATH), HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("3");
        assertThat(resp.getHeaders().getFirst("X-RateLimit-Remaining"))
            .as("remaining décrémenté").isNotNull();
    }
}
