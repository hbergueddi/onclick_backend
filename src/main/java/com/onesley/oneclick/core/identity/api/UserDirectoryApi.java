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

    /**
     * Projection légère d'un utilisateur pour affichage/contact hors module.
     *
     * <p>{@code avatarUrl} ajouté pour « Ma Famille » (PCC Lot 5 — la liste famille affiche
     * la photo du proche). Champ optionnel : null si l'utilisateur n'a pas d'avatar.</p>
     */
    record UserName(UUID id, String firstName, String lastName, String phone, String email, String avatarUrl) {}

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

    /**
     * Résolution d'un membre par identifiant « humain », scopée à un tenant donné.
     * Auto-détecte le format de {@code identifier} :
     * <ul>
     *   <li>contient {@code '@'} → email (insensible à la casse) ;</li>
     *   <li>commence par {@code 'OC-'} (insensible à la casse) → code de parrainage
     *       ({@code referral_code}, comparé en majuscules) ;</li>
     *   <li>sinon → téléphone (espaces / tirets / points retirés avant comparaison).</li>
     * </ul>
     *
     * <p>Toujours filtré {@code tenant_id = :tenantId AND deleted_at IS NULL}. Vide si
     * {@code identifier}/{@code tenantId} null/blank ou aucun membre actif correspondant
     * dans le tenant. Contrat typé pour « Ma Famille » (PCC Lot 5) : résolution du proche
     * à ajouter sans lire la table {@code users} en SQL natif hors module identity.</p>
     */
    Optional<UserName> findByIdentifier(String identifier, UUID tenantId);
}
