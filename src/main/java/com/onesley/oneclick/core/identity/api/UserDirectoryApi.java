package com.onesley.oneclick.core.identity.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P2 — Query-API d'annuaire utilisateur exposée hors module (core.identity OPEN).
 *
 * <p>Contrat typé pour la résolution de noms d'utilisateurs par les autres modules
 * (loyalty wallet-pass, enrollment, etc.). Remplace les lectures SQL natives directes
 * de la table {@code users} (couplage de schéma caché, cassant à la moindre évolution
 * de colonne) par un contrat versionnable et refactor-safe.
 *
 * <p>Projection minimale {@link UserName} (id + nom + contact) — pas d'exposition de
 * l'entité {@code User} ni de son rôle/permissions hors du module identity.
 */
public interface UserDirectoryApi {

    /** Projection légère d'un utilisateur pour affichage/contact hors module. */
    record UserName(UUID id, String firstName, String lastName, String phone, String email) {}

    /** Nom d'un utilisateur actif (soft-delete exclus). Vide si introuvable/supprimé. */
    Optional<UserName> nameById(UUID userId);

    /** Noms de plusieurs utilisateurs actifs en une requête (anti N+1). Ordre non garanti. */
    List<UserName> namesByIds(List<UUID> userIds);

    /**
     * Tenant d'un utilisateur actif (soft-delete exclus). Vide si introuvable/supprimé
     * OU si l'utilisateur est global (admin plateforme sans tenant).
     *
     * <p>Contrat typé pour les modules qui ont besoin du tenant d'un user sans lire la
     * table {@code users} en SQL natif (ex: loyalty punch-cards scopées par tenant).</p>
     */
    Optional<UUID> tenantIdById(UUID userId);
}
