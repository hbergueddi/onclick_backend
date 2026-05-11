package com.onesley.oneclick.loyalty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration CORS — loyalty-service n'a pas de Spring Security pour l'instant
 * (Phase 2 monolithe pré-§6 sécurité), donc on configure CORS directement via
 * WebMvcConfigurer.
 *
 * <p>Origines autorisées en dev local :
 *   - http://localhost:3000  (Nuxt frontend dev)
 *   - http://localhost:8080  (gateway)
 *   - http://localhost:8083  (core direct)
 *
 * <p>Override en prod via env var APP_CORS_ALLOWED_ORIGINS.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080,http://localhost:8083}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins.split(","))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Authorization", "Content-Type")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
