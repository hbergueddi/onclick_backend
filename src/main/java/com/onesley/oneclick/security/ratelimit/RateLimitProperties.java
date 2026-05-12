package com.onesley.oneclick.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration externalisée du rate-limiter — Sprint G.6.1.
 *
 * <p>Mapping {@code app.rate-limit.*} dans application.yml. En dev par défaut
 * désactivé pour ne pas gêner Swagger UI / curl manuel. En prod activé.
 *
 * <p>Exemple :
 * <pre>
 * app:
 *   rate-limit:
 *     enabled: true
 *     defaults:
 *       capacity: 60
 *       refill-period: PT1M
 *     endpoints:
 *       - path: /api/auth/login
 *         capacity: 10
 *         refill-period: PT1M
 *       - path: /api/auth/otp/**
 *         capacity: 5
 *         refill-period: PT15M
 * </pre>
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Active/désactive globalement le rate-limit. Default false (dev-friendly). */
    private boolean enabled = false;

    /** Bucket par défaut appliqué aux endpoints non listés. */
    private Bucket defaults = new Bucket(60, Duration.ofMinutes(1));

    /** Configuration par path (Ant pattern matching). Ordre : premier match gagne. */
    private List<EndpointConfig> endpoints = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Bucket getDefaults() { return defaults; }
    public void setDefaults(Bucket defaults) { this.defaults = defaults; }

    public List<EndpointConfig> getEndpoints() { return endpoints; }
    public void setEndpoints(List<EndpointConfig> endpoints) { this.endpoints = endpoints; }

    /** Definition d'un token bucket : N requêtes par période, refill total à la fin. */
    public static class Bucket {
        private int capacity;
        private Duration refillPeriod;

        public Bucket() {}
        public Bucket(int capacity, Duration refillPeriod) {
            this.capacity = capacity;
            this.refillPeriod = refillPeriod;
        }

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public Duration getRefillPeriod() { return refillPeriod; }
        public void setRefillPeriod(Duration refillPeriod) { this.refillPeriod = refillPeriod; }
    }

    /** Override par endpoint pattern. Path = Ant pattern (ex: {@code /api/auth/login}, {@code /api/auth/otp/**}). */
    public static class EndpointConfig extends Bucket {
        private String path;
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }
}
