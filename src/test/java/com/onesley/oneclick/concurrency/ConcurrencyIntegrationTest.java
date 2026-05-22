package com.onesley.oneclick.concurrency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ACID / concurrence — prouve que les contraintes UNIQUE en base sont le garde-fou
 * atomique sous requêtes simultanées (la vérif applicative seule a une fenêtre de course).
 *
 * <p>On tire N requêtes identiques en parallèle qui devraient être dédupliquées, puis on
 * assert que la base contient <b>exactement 1</b> ligne (jamais de doublon malgré la course).
 */
class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    /** Tire {@code n} appels en parallèle (départ synchronisé) ; retourne le nb de 2xx. */
    private int fireConcurrent(int n, BooleanSupplier call) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger ok = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try { go.await(); if (call.getAsBoolean()) ok.incrementAndGet(); }
                catch (Exception ignored) { }
                finally { done.countDown(); }
            });
        }
        go.countDown(); // top départ simultané
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        return ok.get();
    }

    @Test
    void concurrentFriendshipCreate_uniqueConstraint_keepsExactlyOne() throws Exception {
        String admin = adminBearer();
        List<String> u = jdbc.queryForList(
            "SELECT id::text FROM users WHERE deleted_at IS NULL ORDER BY id LIMIT 2", String.class);
        String a = u.get(0), b = u.get(1);
        String pairSql = "(user1_id=?::uuid AND user2_id=?::uuid) OR (user1_id=?::uuid AND user2_id=?::uuid)";
        jdbc.update("DELETE FROM friendships WHERE " + pairSql, a, b, b, a);

        int ok = fireConcurrent(8, () -> restTemplate.exchange(url("/api/social/friendships"), HttpMethod.POST,
            jsonJwtEntity(Map.of("user1Id", a, "user2Id", b), admin), String.class).getStatusCode().is2xxSuccessful());

        Long count = jdbc.queryForObject("SELECT count(*) FROM friendships WHERE " + pairSql, Long.class, a, b, b, a);
        assertThat(count).as("contrainte unique (user1_id,user2_id) sous 8 requêtes simultanées").isEqualTo(1L);
        assertThat(ok).isGreaterThanOrEqualTo(1);

        jdbc.update("DELETE FROM friendships WHERE " + pairSql, a, b, b, a); // self-clean
    }

    @Test
    void concurrentRsvp_uniqueConstraint_keepsExactlyOne() throws Exception {
        String admin = adminBearer();
        String tenantId = jdbc.queryForObject("SELECT tenant_id::text FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1", String.class);
        String uid = jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class);

        // crée un event à forte capacité
        var post = restTemplate.exchange(url("/api/events"), HttpMethod.POST, jsonJwtEntity(Map.of(
            "tenantId", tenantId, "title", "Concurrence L4",
            "eventAt", Instant.now().plus(7, ChronoUnit.DAYS).toString(),
            "capacity", 1000, "isActive", true), admin), String.class);
        String eventId = om.readTree(post.getBody()).get("id").asText();
        jdbc.update("DELETE FROM event_participations WHERE event_id=?::uuid AND user_id=?::uuid", eventId, uid);

        int ok = fireConcurrent(8, () -> restTemplate.exchange(url("/api/events/participations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("eventId", eventId, "userId", uid, "status", "going"), admin), String.class)
            .getStatusCode().is2xxSuccessful());

        Long count = jdbc.queryForObject(
            "SELECT count(*) FROM event_participations WHERE event_id=?::uuid AND user_id=?::uuid", Long.class, eventId, uid);
        assertThat(count).as("contrainte unique (event_id,user_id) sous 8 RSVP simultanés").isEqualTo(1L);
        assertThat(ok).isGreaterThanOrEqualTo(1);

        // self-clean
        jdbc.update("DELETE FROM event_participations WHERE event_id=?::uuid", eventId);
        restTemplate.exchange(url("/api/events/" + eventId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }
}
