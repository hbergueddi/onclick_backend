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

    /** Vérifie qu'un rôle est présent dans les authorities du user courant. */
    public static boolean hasRole(String roleCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        String expected = "ROLE_" + roleCode;
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(expected));
    }

    /** true si user courant est SUPERADMIN ou GROUP_ADMIN (admin global ou tenant). */
    public static boolean isAdmin() {
        return hasRole("SUPERADMIN") || hasRole("GROUP_ADMIN");
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
