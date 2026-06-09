package com.onesley.oneclick.core.membership.internal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/**
 * Accès au contexte de sécurité <b>via l'API Spring</b> (SecurityContextHolder), partagé par les
 * services du module {@code membership}.
 *
 * <p>On NE passe PAS par notre {@code SecurityHelper} (package {@code security}) : le module
 * {@code security} dépend déjà de {@code membership.api} (pliage des authorities P1), donc
 * {@code membership → security} créerait un cycle Modulith. Ces helpers n'utilisent que des classes
 * Spring (hors modules applicatifs), ce qui préserve les frontières.</p>
 */
final class MembershipSecurityContext {

    private MembershipSecurityContext() {}

    /** UUID du sujet du JWT courant, ou {@code null} si non authentifié / sub invalide. */
    static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                return UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    /** Vrai si le contexte courant détient l'autorité donnée ({@code VERB:RESOURCE}). */
    static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
