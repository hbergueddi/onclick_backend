package com.onesley.oneclick.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration sécurité minimale — squelette Phase 2.
 *
 * <p>Politique actuelle (volontairement permissive) :
 * <ul>
 *   <li>CSRF désactivé (API REST stateless, pas de cookies de session)</li>
 *   <li>Sessions stateless (pas de {@code JSESSIONID})</li>
 *   <li>CORS autorise React dev ({@code localhost:8080}) + prod ({@code app-oneclick.net})</li>
 *   <li>Toutes les routes en {@code permitAll()} — la sécurité réelle (rôles + scopes JWT)
 *       arrivera en Phase 3 avec le module {@code auth}.</li>
 *   <li>Endpoints actuator (health/info) explicitement publics ; le reste reste filtrable
 *       plus tard via {@code management.endpoints.web.exposure.include}.</li>
 * </ul>
 *
 * <p><b>Ne pas activer OAuth2 Resource Server ici</b> tant qu'on n'a pas un issuer JWT
 * (notre service auth Phase 3). Spring échouerait au démarrage si {@code spring.security
 * .oauth2.resourceserver.jwt.*} est configuré sans JWK valide.
 */
@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:8080,http://localhost:5173,https://app-oneclick.net}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Documentation API
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Actuator public (health, info, metrics)
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/metrics/**").permitAll()
                // Preflight CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Phase 2 : tout en permitAll, sera resserré en Phase 3+
                .anyRequest().permitAll()
            )
            // Pas de formLogin ni httpBasic — API token-based uniquement (à venir)
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Type", "X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
