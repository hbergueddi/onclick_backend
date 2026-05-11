package com.onesley.oneclick.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway — Phase 2.7 §21 spec senior dev.
 *
 * <p>Point d'entrée unique pour les clients front (apps mobile + web).
 * Route les requêtes vers le microservice approprié selon le path prefix.
 *
 * <p>Port 8080. Aucune logique métier — uniquement du routage HTTP réactif via
 * Spring Cloud Gateway (Netty/WebFlux, pas Tomcat/Servlet).
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
