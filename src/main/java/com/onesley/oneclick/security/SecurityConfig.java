package com.onesley.oneclick.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

    @Value("${app.cors.allowed-origins:http://localhost:8080,http://localhost:5173,https://app-oneclick.net}")
    private String allowedOrigins;

    @Value("${app.security.oauth2.enabled:false}")
    private boolean oauth2Enabled;

    private final UserRoleAuthoritiesConverter authoritiesConverter;

    public SecurityConfig(
        org.springframework.beans.factory.ObjectProvider<UserRoleAuthoritiesConverter> converterProvider
    ) {
        // ObjectProvider permet d'injecter optionnellement — le bean
        // UserRoleAuthoritiesConverter existe toujours (Component scan), mais
        // on ne l'utilise que si oauth2Enabled. Pas de @Lazy nécessaire.
        this.authoritiesConverter = converterProvider.getIfAvailable();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        if (oauth2Enabled) {
            http.authorizeHttpRequests(auth -> auth
                // Documentation API toujours publique
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Health/info publics, le reste de l'actuator authentifié
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
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
