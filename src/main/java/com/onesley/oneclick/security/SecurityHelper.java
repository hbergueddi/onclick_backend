package com.onesley.oneclick.security;

import com.onesley.oneclick.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/**
 * Helper pour les vérifications RBAC applicatives (en complément de
 * {@code @PreAuthorize} qui couvre les contrôles déclaratifs par rôle).
 *
 * <p>Usage typique — vérification d'ownership pour les endpoints OWNER :</p>
 *
 * <pre>{@code
 * @PatchMapping("/{id}")
 * @PreAuthorize("isAuthenticated()")
 * public UserDto update(@PathVariable UUID id, ...) {
 *     SecurityHelper.requireOwnerOrAdmin(id);   // 403 si current != id et pas SUPERADMIN
 *     return service.update(id, ...);
 * }
 * }</pre>
 *
 * <p>Phase 6.2 §spec senior dev — sécurité granulaire au-delà de Spring Method Security.</p>
 */
public final class SecurityHelper {

    private SecurityHelper() {}

    /** UUID du user courant (depuis le sub du JWT), ou null si pas authentifié. */
    public static UUID currentUserId() {
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

    /**
     * true si le user courant a une portée ADMINISTRATIVE (global ou tenant).
     *
     * <p>Audit R4 (directive #2 : 100 % {@code hasAuthority}, jamais de {@code hasRole}/rôle).
     * Discriminant exprimé via l'autorité {@code VIEW:USERS} — « voir n'importe quel utilisateur »
     * est par conception réservé aux admins (SUPERADMIN + GROUP_ADMIN) et à eux seuls (vérifié sur
     * le catalogue RBAC : aucune autre population ne la détient). Remplace l'ancien
     * {@code hasRole("SUPERADMIN") || hasRole("GROUP_ADMIN")} sans changement de comportement.</p>
     */
    public static boolean isAdmin() {
        return hasAuthority("VIEW:USERS");
    }

    /**
     * true si le user courant agit côté « gestion » (admin OU staff opérationnel),
     * par opposition à un membre/client.
     *
     * <p>Audit R4 : exprimé via l'autorité {@code VIEW:STAFF} — « voir l'équipe » est détenue par
     * toute la population gestion (SUPERADMIN, GROUP_ADMIN, RESTAURATEUR, STAFF) et JAMAIS par le
     * CLIENT (vérifié sur le catalogue RBAC). Remplace l'ancien {@code isAdmin() || hasRole("STAFF")
     * || hasRole("RESTAURATEUR")} sans changement de comportement. Le CLIENT → {@code false} (self-scope).</p>
     */
    public static boolean isStaffOrAdmin() {
        return hasAuthority("VIEW:STAFF");
    }

    /** Vrai si le user courant détient l'autorité {@code ACTION:MENU} donnée (ex: "CREATE:LOYALTY"). */
    public static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(authority));
    }

    /**
     * Lance 403 sauf si : le user courant est l'owner, OU détient l'autorité donnée, OU est admin.
     * <p>Pour les lectures « owner par défaut, mais aussi accessibles à une population staff
     * identifiée par une autorité » — ex: la réputation d'un client (scores/ratings) lisible par
     * les staff qui notent les clients ({@code CREATE:LOYALTY}), sans exposer aux autres clients.</p>
     */
    public static void requireSelfOrAuthorityOrAdmin(UUID resourceOwnerId, String authority) {
        UUID current = currentUserId();
        if (current == null) {
            throw new ForbiddenException("Authentification requise");
        }
        if (current.equals(resourceOwnerId)) return;
        if (hasAuthority(authority)) return;
        if (isAdmin()) return;
        throw new ForbiddenException(
            "Accès interdit : vous n'êtes pas propriétaire de cette ressource"
        );
    }

    /**
     * Lance 403 si le user courant n'est ni l'owner désigné ni un admin.
     * <p>Pattern recommandé pour les endpoints OWNER (GET/PATCH/DELETE d'une ressource
     * possédée par un user — ex: PATCH /api/users/{id}).</p>
     */
    public static void requireOwnerOrAdmin(UUID resourceOwnerId) {
        UUID current = currentUserId();
        if (current == null) {
            throw new ForbiddenException("Authentification requise");
        }
        if (current.equals(resourceOwnerId)) return;
        if (isAdmin()) return;
        throw new ForbiddenException(
            "Accès interdit : vous n'êtes pas propriétaire de cette ressource"
        );
    }

    /**
     * Variante stricte : seul le owner exact peut passer (les admins NON).
     * <p>Utile pour les opérations sensibles (ex: changement de mot de passe).</p>
     */
    public static void requireOwnerExact(UUID resourceOwnerId) {
        UUID current = currentUserId();
        if (current == null || !current.equals(resourceOwnerId)) {
            throw new ForbiddenException(
                "Accès interdit : opération réservée au propriétaire"
            );
        }
    }
}
