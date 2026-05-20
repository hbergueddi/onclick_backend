package com.onesley.oneclick.realtime;

import com.onesley.oneclick.security.UserRoleAuthoritiesConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Bug 37 — Authentification + autorisation du canal STOMP entrant.
 *
 * <p>Deux responsabilités, sur le frame STOMP (pas sur le handshake HTTP) :
 * <ol>
 *   <li><b>CONNECT</b> : lit le header natif {@code Authorization: Bearer <jwt>},
 *       valide via le {@link JwtDecoder} existant, convertit en
 *       {@link Authentication} (autorités RBAC) via {@link UserRoleAuthoritiesConverter},
 *       et l'attache à la session WebSocket ({@code accessor.setUser}).</li>
 *   <li><b>SUBSCRIBE</b> : sur les topics sensibles ({@code /topic/admin/**}),
 *       vérifie que l'utilisateur a l'autorité requise (SUPERADMIN) — sinon
 *       rejette l'abonnement.</li>
 * </ol>
 *
 * <p>Mode dev (oauth2 désactivé) : permissif, comme {@code SecurityConfig}
 * (pas de {@link JwtDecoder} bean → on ne valide rien, on laisse passer). En
 * prod ({@code app.security.oauth2.enabled=true}) : strict.
 */
@Component
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String ADMIN_TOPIC_PREFIX = "/topic/admin";
    private static final String SUPERADMIN_AUTHORITY = "SUPERADMIN";

    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final UserRoleAuthoritiesConverter authoritiesConverter;

    @Value("${app.security.oauth2.enabled:false}")
    private boolean oauth2Enabled;

    public StompAuthChannelInterceptor(
        ObjectProvider<JwtDecoder> jwtDecoderProvider,
        UserRoleAuthoritiesConverter authoritiesConverter
    ) {
        this.jwtDecoderProvider = jwtDecoderProvider;
        this.authoritiesConverter = authoritiesConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            default -> { /* no-op */ }
        }
        return message;
    }

    /** Valide le JWT du frame CONNECT et attache l'Authentication à la session. */
    private void authenticate(StompHeaderAccessor accessor) {
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        if (decoder == null) {
            // Dev permissif : pas de validation (mirror SecurityConfig permitAll).
            return;
        }
        String bearer = accessor.getFirstNativeHeader("Authorization");
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            throw new MessagingException("STOMP CONNECT sans Bearer token");
        }
        try {
            Jwt jwt = decoder.decode(bearer.substring(7));
            AbstractAuthenticationToken auth = authoritiesConverter.convert(jwt);
            accessor.setUser(auth);
        } catch (JwtException e) {
            log.warn("[ws] CONNECT rejeté — JWT invalide : {}", e.getMessage());
            throw new MessagingException("JWT invalide", e);
        }
    }

    /** Autorise l'abonnement aux topics sensibles. */
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null || !dest.startsWith(ADMIN_TOPIC_PREFIX)) {
            return; // topic non sensible
        }
        if (jwtDecoderProvider.getIfAvailable() == null && !oauth2Enabled) {
            return; // dev permissif
        }
        Principal user = accessor.getUser();
        if (!(user instanceof Authentication auth) || !hasAuthority(auth, SUPERADMIN_AUTHORITY)) {
            log.warn("[ws] SUBSCRIBE {} refusé — autorité SUPERADMIN requise", dest);
            throw new MessagingException("Accès refusé : " + dest + " requiert " + SUPERADMIN_AUTHORITY);
        }
    }

    private boolean hasAuthority(Authentication auth, String authority) {
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (authority.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
