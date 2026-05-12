package com.onesley.oneclick.security.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Token bucket algorithm — implémentation maison thread-safe (Sprint G.6.1).
 *
 * <p>Pourquoi pas Bucket4j ? Les artifactIds Bucket4j 8.x ont changé plusieurs
 * fois (bucket4j-core / bucket4j_jdk17-core selon la version) et le risque
 * de conflit avec Spring Boot 4 est non négligeable. Cette implémentation
 * fait exactement ce qu'on a besoin en ~80 lignes, zero dépendance externe.
 *
 * <p>Algorithme classique :
 * <ul>
 *   <li>Capacité {@code capacity} tokens max</li>
 *   <li>Refill complet (greedy) toutes les {@code refillPeriod}</li>
 *   <li>Au moment du {@link #tryConsume(int)}, on calcule le refill avant de tenter</li>
 * </ul>
 *
 * <p>Thread safety : {@link AtomicReference} sur l'état immutable (tokens + lastRefill).
 * Compare-and-swap loop sur tryConsume. Pas de lock. ~50 ns par appel.
 *
 * <p>Pas idéal pour multi-instance (chaque pod a son propre bucket → 2x limite si
 * 2 pods). V2 : Bucket4j-Redis avec backend partagé.
 */
public final class TokenBucket {

    private final int capacity;
    private final Duration refillPeriod;
    private final AtomicReference<State> state;

    public TokenBucket(int capacity, Duration refillPeriod) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (refillPeriod == null || refillPeriod.isNegative() || refillPeriod.isZero()) {
            throw new IllegalArgumentException("refillPeriod must be > 0");
        }
        this.capacity = capacity;
        this.refillPeriod = refillPeriod;
        this.state = new AtomicReference<>(new State(capacity, Instant.now()));
    }

    /**
     * Tente de consumer {@code n} tokens. Retourne un {@link Probe} avec le résultat
     * et le nombre de tokens restants OU le délai d'attente avant le prochain refill.
     */
    public Probe tryConsume(int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be > 0");

        Instant now = Instant.now();

        while (true) {
            State current = state.get();

            // 1. Calculer le refill depuis lastRefill
            int refilledTokens = computeRefill(current, now);
            int availableTokens = Math.min(capacity, current.tokens + refilledTokens);

            // 2. Recalculer le lastRefill : si on a refill, c'est NOW. Sinon on garde l'ancien.
            Instant newLastRefill = refilledTokens > 0 ? now : current.lastRefill;

            if (availableTokens >= n) {
                // Consommation OK
                State next = new State(availableTokens - n, newLastRefill);
                if (state.compareAndSet(current, next)) {
                    return new Probe(true, availableTokens - n, Duration.ZERO);
                }
                // CAS failed, retry
            } else {
                // Pas assez de tokens — calculer le délai jusqu'au prochain refill
                Duration waitUntilNextRefill = refillPeriod.minus(Duration.between(newLastRefill, now));
                if (waitUntilNextRefill.isNegative()) waitUntilNextRefill = Duration.ZERO;
                // Pas de CAS — on ne change pas l'état
                return new Probe(false, availableTokens, waitUntilNextRefill);
            }
        }
    }

    /** Combien de tokens à ajouter selon le temps écoulé depuis lastRefill. */
    private int computeRefill(State state, Instant now) {
        if (now.isBefore(state.lastRefill)) return 0;
        Duration elapsed = Duration.between(state.lastRefill, now);
        // Refill greedy : 1 période entière écoulée = refill complet
        long periodsElapsed = elapsed.toNanos() / refillPeriod.toNanos();
        if (periodsElapsed <= 0) return 0;
        return capacity; // refill complet (greedy strategy)
    }

    public int getCapacity() { return capacity; }
    public Duration getRefillPeriod() { return refillPeriod; }

    /** Snapshot de l'état du bucket (immutable, AtomicReference-friendly). */
    private record State(int tokens, Instant lastRefill) {}

    /** Résultat d'un {@link #tryConsume(int)}. */
    public record Probe(boolean consumed, int remainingTokens, Duration waitUntilRefill) {}
}
