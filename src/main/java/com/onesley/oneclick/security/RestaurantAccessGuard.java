package com.onesley.oneclick.security;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Garde-fou ABAC réutilisable — tenance par-restaurant (modèle « dual-ownership »).
 *
 * <p>Complète le RBAC grossier ({@code @PreAuthorize hasAuthority('VERB:RESOURCE')})
 * par une vérification d'appartenance : pour les ressources possédées par un
 * restaurant (réservations, loyalty, financier, staff/services/zones/tables), un
 * non-admin ne doit accéder qu'aux restaurants dont il est <b>staff actif</b>
 * ({@code restaurant_staffs}). Les admins (SUPERADMIN/GROUP_ADMIN) passent toujours.
 *
 * <p>Centralise le pattern qui n'existait que dupliqué en privé dans
 * {@code LoyaltyExtensionService.requireAdminOrStaffOf} — réutilisé par tous les
 * services/contrôleurs scoping par restaurant (P2 owner-check sweep).
 *
 * <h3>Primitive ABAC infra — exception architecturale DOCUMENTÉE (P2)</h3>
 * <p>Vit dans {@code security} (module infra <b>OPEN</b>) et lit {@code restaurant_staffs}
 * en SQL natif <b>volontairement</b> : c'est la primitive d'autorisation transverse de la
 * plateforme. La placer ici (plutôt que dans {@code modules.restaurant}) permet à TOUT module
 * business de scoper l'accès par restaurant <b>sans introduire de dépendance inter-module
 * business</b> — l'invariant « 0 dépendance business↔business » de la plateforme tient
 * (cf {@code modules.analytics} package-info). Vérifie l'appartenance de
 * l'<b>utilisateur courant</b> (throw-or-pass), pas d'un {@code userId} arbitraire.
 */
@Component
public class RestaurantAccessGuard {

    @PersistenceContext
    private EntityManager em;

    /** true si le user courant est admin OU staff actif du restaurant donné. */
    public boolean isAdminOrActiveStaffOf(UUID restaurantId) {
        if (SecurityHelper.isAdmin()) return true;
        UUID callerId = SecurityHelper.currentUserId();
        if (callerId == null || restaurantId == null) return false;
        Number count = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM restaurant_staffs "
                + "WHERE user_id = :userId AND restaurant_id = :restaurantId AND deleted_at IS NULL")
            .setParameter("userId", callerId)
            .setParameter("restaurantId", restaurantId)
            .getSingleResult();
        return count.longValue() > 0;
    }

    /**
     * 403 si le user courant n'est ni admin ni staff actif du restaurant.
     * 400 si {@code restaurantId} est null pour un non-admin (scope obligatoire).
     */
    public void requireAdminOrActiveStaffOf(UUID restaurantId) {
        if (SecurityHelper.isAdmin()) return;
        if (SecurityHelper.currentUserId() == null) {
            throw new ForbiddenException("Authentification requise");
        }
        if (restaurantId == null) {
            throw new BadRequestException(
                "restaurantId obligatoire pour un non-admin (scope par restaurant)");
        }
        if (!isAdminOrActiveStaffOf(restaurantId)) {
            throw new ForbiddenException(
                "Accès refusé : vous n'êtes pas staff de ce restaurant");
        }
    }
}
