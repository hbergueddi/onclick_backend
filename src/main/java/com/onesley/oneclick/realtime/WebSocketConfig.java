package com.onesley.oneclick.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Bug 37 — Configuration WebSocket + STOMP pour les dashboards temps réel.
 *
 * <p>Remplace le Realtime Supabase (postgres_changes) par un push natif depuis
 * notre backend Spring + Postgres. Les KPI sont poussés sur des topics STOMP :
 * <ul>
 *   <li>{@code /topic/admin/exec} — dashboard exécutif admin (SUPERADMIN)</li>
 *   <li>{@code /topic/restaurant/{id}/cockpit} — cockpit resto (à venir)</li>
 *   <li>{@code /user/queue/...} — push par-utilisateur (client, à venir)</li>
 * </ul>
 *
 * <p>Endpoint de handshake : {@code /ws} (whitelisté dans {@code SecurityConfig}).
 * L'authentification JWT se fait au niveau du frame STOMP {@code CONNECT} via
 * {@link StompAuthChannelInterceptor} (et non au handshake HTTP), ce qui permet
 * de passer le Bearer token dans les headers STOMP natifs.
 *
 * <p>Broker « simple » in-memory (suffisant pour 1 instance). Si on scale en
 * multi-pod plus tard, basculer sur un relais broker externe (RabbitMQ/Redis)
 * sans toucher au reste — c'est l'intérêt de STOMP.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint de handshake. setAllowedOriginPatterns aligné sur le CORS HTTP.
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(
                "http://localhost:*", "https://app-oneclick.net", "https://*.app-oneclick.net"
            );
        // Pas de withSockJS() : les clients modernes (navigateur + @stomp/stompjs)
        // supportent WebSocket natif. À ajouter seulement si fallback legacy requis.
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Destinations broker → /topic (broadcast) + /queue (par-user via /user)
        registry.enableSimpleBroker("/topic", "/queue");
        // Préfixe des messages entrants (client → serveur) — non utilisé en v1
        // (dashboards = push pur serveur → client), mais déclaré pour la suite.
        registry.setApplicationDestinationPrefixes("/app");
        // Préfixe des destinations par-utilisateur (/user/queue/...)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Authentifie le CONNECT + autorise les SUBSCRIBE sensibles.
        registration.interceptors(authInterceptor);
    }
}
