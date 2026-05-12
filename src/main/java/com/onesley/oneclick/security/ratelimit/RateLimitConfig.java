package com.onesley.oneclick.security.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Active {@link RateLimitProperties} en tant que bean Spring — Sprint G.6.1.
 *
 * <p>Le {@link RateLimitFilter} est enregistré explicitement dans
 * {@code SecurityConfig.securityFilterChain} pour qu'il s'exécute AVANT
 * le filtre JWT (sinon on consomme des CPU sur de l'auth + DB pour des
 * requêtes destinées à être 429-bloquées).
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {
}
