package com.onesley.oneclick.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires de {@link TokenBucket} — Sprint G.6.1.
 *
 * <p>Cas couverts :
 * <ul>
 *   <li>Consommation jusqu'à épuisement → probe.consumed=false</li>
 *   <li>Refill après période → bucket plein à nouveau</li>
 *   <li>Validation params (capacity > 0, refillPeriod > 0)</li>
 *   <li>Thread safety (concurrent tryConsume)</li>
 * </ul>
 */
class TokenBucketTest {

    @Test
    void consumeUntilEmpty() {
        TokenBucket bucket = new TokenBucket(3, Duration.ofMinutes(1));
        assertThat(bucket.tryConsume(1).consumed()).isTrue();
        assertThat(bucket.tryConsume(1).consumed()).isTrue();
        TokenBucket.Probe last = bucket.tryConsume(1);
        assertThat(last.consumed()).isTrue();
        assertThat(last.remainingTokens()).isZero();

        // 4e appel → bucket vide
        TokenBucket.Probe denied = bucket.tryConsume(1);
        assertThat(denied.consumed()).isFalse();
        assertThat(denied.remainingTokens()).isZero();
        assertThat(denied.waitUntilRefill()).isPositive();
    }

    @Test
    void consumeMultipleTokensAtOnce() {
        TokenBucket bucket = new TokenBucket(10, Duration.ofMinutes(1));
        // Burst 5
        assertThat(bucket.tryConsume(5).consumed()).isTrue();
        assertThat(bucket.tryConsume(5).consumed()).isTrue();
        // 11e → fail (10 tokens consumés)
        assertThat(bucket.tryConsume(1).consumed()).isFalse();
    }

    @Test
    void refillAfterPeriod() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(2, Duration.ofMillis(100));
        assertThat(bucket.tryConsume(1).consumed()).isTrue();
        assertThat(bucket.tryConsume(1).consumed()).isTrue();
        assertThat(bucket.tryConsume(1).consumed()).isFalse(); // vide

        // Attendre 150ms (> refillPeriod) → refill
        Thread.sleep(150);
        assertThat(bucket.tryConsume(1).consumed()).isTrue();
        assertThat(bucket.tryConsume(1).consumed()).isTrue();
        assertThat(bucket.tryConsume(1).consumed()).isFalse(); // re-vide
    }

    @Test
    void validateConstructorParams() {
        assertThatThrownBy(() -> new TokenBucket(0, Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(-1, Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(10, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(10, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(10, Duration.ofMinutes(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tryConsumeRejectsZeroOrNegative() {
        TokenBucket bucket = new TokenBucket(10, Duration.ofMinutes(1));
        assertThatThrownBy(() -> bucket.tryConsume(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucket.tryConsume(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentConsumeRespectsCapacity() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(100, Duration.ofMinutes(1));
        int threads = 10;
        int callsPerThread = 50; // 10*50 = 500 attempts, mais capacity=100 → max 100 OK

        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < callsPerThread; j++) {
                        if (bucket.tryConsume(1).consumed()) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();

        // Exactement 100 succès (capacity), pas un de plus
        assertThat(successCount.get()).isEqualTo(100);
    }
}
