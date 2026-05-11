package com.onesley.oneclick.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Bean {@link JwtDecoder} — valide les JWT Supabase HS256.
 *
 * <p>Activé uniquement quand {@code app.security.oauth2.enabled=true}. Sinon le
 * SecurityConfig garde le mode permissif Phase 2.6 (toutes les routes en
 * {@code permitAll}, idéal pour le dev local + Swagger UI sans header).
 *
 * <p><b>Pourquoi HS256 et pas RS256/JWK Set</b> : Supabase Auth signe les JWT
 * avec un secret partagé (HS256) par défaut, pas avec une paire clé/clé publique.
 * On ne peut donc pas utiliser {@code jwk-set-uri}. Le secret se configure via
 * la variable d'environnement {@code JWT_SECRET} (ou property
 * {@code app.security.jwt-secret}). Ce secret se trouve dans le dashboard
 * Supabase → Settings → API → "JWT Settings" → "JWT Secret".
 *
 * <p>Quand on aura notre propre service auth (Phase 11+), on pourra basculer
 * vers RS256 + JWK Set sans toucher au reste du code (juste remplacer ce bean).
 */
@Configuration
@ConditionalOnProperty(name = "app.security.oauth2.enabled", havingValue = "true")
public class JwtConfig {

    @Value("${app.security.jwt-secret}")
    private String jwtSecret;

    @Bean
    public JwtDecoder jwtDecoder() {
        // Supabase utilise HS256 (HMAC SHA-256) avec un secret au moins 256 bits
        SecretKeySpec key = new SecretKeySpec(
            jwtSecret.getBytes(StandardCharsets.UTF_8),
            MacAlgorithm.HS256.getName()
        );
        return NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }
}
