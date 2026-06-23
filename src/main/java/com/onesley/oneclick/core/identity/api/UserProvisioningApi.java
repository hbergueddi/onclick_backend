package com.onesley.oneclick.core.identity.api;

import java.util.UUID;

/**
 * BE-2 (plan RESTAURANT-ONBOARDING) — port de <b>provisioning</b> de compte exposé hors module
 * (core.identity OPEN). Pendant « write » de {@link UserDirectoryApi} (qui ne fait que de la lecture).
 *
 * <p>Permet à un module métier (ex. {@code modules.store}, à l'approbation d'une demande
 * d'inscription d'enseigne) de créer un compte gérant <b>sans lire/écrire la table {@code users} en
 * SQL natif</b> ni dépendre de l'implémentation interne d'identity. Le compte est créé avec :
 * <ul>
 *   <li>le rôle {@code RESTAURATEUR} ;</li>
 *   <li>un mot de passe <b>temporaire</b> (fourni en clair, encodé BCrypt par identity) ;</li>
 *   <li>le drapeau {@code password_must_change=true} (BE-3) → le gérant définit son mot de passe au
 *       1er login.</li>
 * </ul>
 *
 * <p>Lève {@code ConflictException} si l'email (ou le téléphone) est déjà pris — l'appelant
 * (admin qui approuve) reçoit alors un 409 propre, et rien n'est créé.
 */
public interface UserProvisioningApi {

    /**
     * Données nécessaires au provisioning d'un compte gérant restaurateur.
     *
     * @param tenantId    tenant d'appartenance (peut être null = compte global) — pour l'onboarding
     *                    OneClick standard, c'est le tenant {@code oneclick}/null selon la demande.
     * @param email       email de connexion (identifiant), obligatoire
     * @param firstName   prénom
     * @param lastName    nom
     * @param phone       téléphone (nullable)
     * @param rawPassword mot de passe temporaire EN CLAIR (encodé BCrypt par identity)
     */
    record ProvisionOwnerCommand(
        UUID tenantId, String email, String firstName, String lastName, String phone, String rawPassword) {}

    /**
     * Crée le compte gérant (rôle {@code RESTAURATEUR}, mdp temporaire, {@code password_must_change=true}).
     * @return l'id du compte créé.
     */
    UUID provisionOwner(ProvisionOwnerCommand cmd);
}
