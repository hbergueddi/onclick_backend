package com.onesley.oneclick.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;

/**
 * Configuration du gateway HTTP custom (Phase 2.7 §21 spec senior).
 *
 * <p>Spring Cloud Gateway n'étant pas compatible Spring Boot 4 à ce jour, on
 * implémente le routage manuellement via {@link RouterFunction} WebFlux et
 * {@link WebClient}. Performance Netty/réactive identique à SCG mais 100 lignes
 * de code au lieu d'un starter complet.
 *
 * <p>Pour chaque prefix matché, on forward la requête vers l'URI du backend
 * en préservant méthode HTTP, headers, et body. La réponse est renvoyée verbatim.
 */
@Configuration
public class GatewayConfig {

    /**
     * Map prefix → backend URI base. Ordre critique : routes spécifiques d'abord,
     * catchall en dernier ({@code /api/**} → core).
     */
    private static final List<Map.Entry<String, String>> ROUTES = List.of(
        Map.entry("/api/notifications", "http://localhost:8084"),
        Map.entry("/api/loyalty",       "http://localhost:8085"),
        Map.entry("/api/payments",      "http://localhost:8086"),
        Map.entry("/api/search",        "http://localhost:8087"),
        Map.entry("/api",               "http://localhost:8083"), // catchall → core
        Map.entry("/v3/api-docs",       "http://localhost:8083"), // OpenAPI (V1: core)
        Map.entry("/swagger-ui",        "http://localhost:8083"),
        Map.entry("/swagger-ui.html",   "http://localhost:8083")
    );

    @Bean
    public WebClient gatewayWebClient() {
        return WebClient.builder().build();
    }

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes(WebClient webClient) {
        // Chaîne de routes : pour chaque prefix, route les requêtes via WebClient
        RouterFunction<ServerResponse> chain = null;
        for (var route : ROUTES) {
            RouterFunction<ServerResponse> rf = RouterFunctions.route(
                path(route.getKey() + "/**").or(path(route.getKey())),
                req -> proxyRequest(webClient, route.getValue(), req)
            );
            chain = chain == null ? rf : chain.and(rf);
        }
        return chain;
    }

    /**
     * Forward la requête vers le backend en préservant méthode + headers + body.
     */
    private reactor.core.publisher.Mono<ServerResponse> proxyRequest(
        WebClient webClient, String backendBaseUri,
        org.springframework.web.reactive.function.server.ServerRequest req) {

        // URI cible = backendBaseUri + path + query
        URI uri = URI.create(backendBaseUri + req.uri().getRawPath()
            + (req.uri().getRawQuery() != null ? "?" + req.uri().getRawQuery() : ""));

        var spec = webClient.method(req.method())
            .uri(uri)
            .headers(h -> h.addAll(req.headers().asHttpHeaders()))
            .body(req.bodyToMono(byte[].class).switchIfEmpty(reactor.core.publisher.Mono.empty()), byte[].class);

        return spec.exchangeToMono(resp -> {
            HttpStatusCode status = resp.statusCode();
            return resp.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                .flatMap(body -> ServerResponse.status(status)
                    .headers(h -> h.addAll(resp.headers().asHttpHeaders()))
                    .bodyValue(body));
        });
    }
}
