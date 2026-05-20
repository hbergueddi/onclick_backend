package com.onesley.oneclick.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.onesley.oneclick.security.ratelimit.RateLimitFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration sécurité — comportement contrôlé par {@code app.security.oauth2.enabled}.
 *
 * <p><b>Mode dev (par défaut, {@code app.security.oauth2.enabled=false})</b> :
 * <ul>
 *   <li>Toutes les routes en {@code permitAll()} — pratique pour Swagger UI,
 *       curl, dev local</li>
 *   <li>OAuth2 Resource Server <i>désactivé</i> (Spring n'essaie pas de valider
 *       de JWT, pas de bean {@link org.springframework.security.oauth2.jwt.JwtDecoder}
 *       requis)</li>
 *   <li>{@code @PreAuthorize} sur les méthodes service/controller fonctionnent
 *       toujours (on a {@link EnableMethodSecurity}) mais sans utilisateur
 *       authentifié, ils refuseront tout — donc ne pas annoter en dev.</li>
 * </ul>
 *
 * <p><b>Mode prod ({@code app.security.oauth2.enabled=true})</b> :
 * <ul>
 *   <li>OAuth2 Resource Server activé avec {@link JwtConfig}</li>
 *   <li>Endpoints publics whitelistés explicitement (Swagger, /actuator/health)</li>
 *   <li>Toutes les autres routes nécessitent un JWT Bearer valide</li>
 *   <li>JWT → autorités via {@link UserRoleAuthoritiesConverter}</li>
 *   <li>{@code @PreAuthorize("hasRole('admin')")} et autres expressions
 *       fonctionnent contre les rôles chargés depuis la DB</li>
 * </ul>
 *
 * <p>Bascule en runtime via env var :
 * {@code APP_SECURITY_OAUTH2_ENABLED=true APP_SECURITY_JWT_SECRET=xxx ...}
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080,http://localhost:5173,https://app-oneclick.net}")
    private String allowedOrigins;

    @Value("${app.security.oauth2.enabled:false}")
    private boolean oauth2Enabled;

    private final UserRoleAuthoritiesConverter authoritiesConverter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(
        org.springframework.beans.factory.ObjectProvider<UserRoleAuthoritiesConverter> converterProvider,
        org.springframework.beans.factory.ObjectProvider<RateLimitFilter> rateLimitFilterProvider
    ) {
        // ObjectProvider permet d'injecter optionnellement — le bean
        // UserRoleAuthoritiesConverter existe toujours (Component scan), mais
        // on ne l'utilise que si oauth2Enabled. Pas de @Lazy nécessaire.
        this.authoritiesConverter = converterProvider.getIfAvailable();
        // RateLimitFilter conditionnel sur app.rate-limit.enabled=true (par défaut désactivé)
        this.rateLimitFilter = rateLimitFilterProvider.getIfAvailable();
    }

    @Bean
    @ConditionalOnWebApplication
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        // Sprint G.6.1 — Rate limit AVANT JWT (anti-bruteforce sans charger le CPU
        // sur de l'auth pour des requêtes qui seront 429-bloquées de toute façon).
        if (rateLimitFilter != null) {
            http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        }

        if (oauth2Enabled) {
            http.authorizeHttpRequests(auth -> auth
                // Documentation API toujours publique
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/auth/**").permitAll()  // Phase 4 §2 — login/refresh/otp publics
                // Bug 37 — handshake WebSocket public ; l'auth JWT se fait au frame
                // STOMP CONNECT (StompAuthChannelInterceptor), pas au handshake HTTP.
                .requestMatchers("/ws/**").permitAll()
                // Health/info publics, le reste de l'actuator authentifié
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // Catalogue public — Login.tsx picker resto avant authent (GET only)
                .requestMatchers(HttpMethod.GET, "/api/restaurants", "/api/restaurants/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/search/restaurants", "/api/search/restaurants/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tenants/by-slug").permitAll()  // whitelabel routing avant login
                .requestMatchers(HttpMethod.POST, "/api/store/onboarding").permitAll()  // Sprint I.3 — formulaire public soumission resto
                .requestMatchers(HttpMethod.POST, "/api/audit/telemetry").permitAll()  // Sprint I.3 — ingestion telemetry sans auth
                .requestMatchers(HttpMethod.GET, "/api/restaurants/featured").permitAll()  // Sprint H — curation publique Explore
                // Preflight CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Tout le reste demande un JWT valide
                .anyRequest().authenticated()
            );
            http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {
                    if (authoritiesConverter != null) {
                        jwt.jwtAuthenticationConverter(authoritiesConverter);
                    }
                })
            );
        } else {
            // Mode dev — permissif, comme en Phase 2.6
            http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/auth/**").permitAll()  // Phase 4 §2 — login/refresh/otp publics
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/metrics/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().permitAll()
            );
        }

        return http.build();
    }

    /**
     * Password encoder pour User#passwordHash — BCrypt strength 12 (recommandé 2024+).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @ConditionalOnWebApplication
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        // OPTIONS retiré : c'est le verbe du preflight CORS lui-même, géré
        // automatiquement par Spring CorsFilter avant le SecurityFilterChain
        // — pas besoin de l'exposer en méthode "business" autorisée.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        // Allowlist explicite des en-têtes que le frontend envoie réellement.
        // Best practice CORS senior : pas de wildcard "*" qui accepte toute
        // requête custom (vecteur d'attaque CSRF / header injection en cas de
        // mauvaise config downstream).
        //
        // Audit frontend OneClick (axios) :
        //  - Authorization     → JWT bearer (intercepteur api/client.ts)
        //  - Content-Type      → application/json sur POST/PATCH/PUT
        //  - Accept            → content negotiation
        //  - X-Requested-With  → défensif (legacy axios / libs tierces)
        //
        // Les en-têtes browser-managed (Origin, Cookie, Referer, User-Agent,
        // Host) ne sont PAS dans cette liste — ils sont gérés implicitement
        // par le navigateur et ne passent pas par le filtre CORS allowedHeaders.
        config.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Requested-With"
        ));
        config.setExposedHeaders(List.of("Authorization", "Content-Type", "X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
