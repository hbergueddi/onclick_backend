package com.onesley.oneclick.core.membership.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Membre d'un programme (tenant) — projection pour la page « Membres » de l'admin tenant (P3).
 * Identité enrichie via {@code UserDirectoryApi} (contrat identity), pas de JOIN users cross-module.
 *
 * @param userId      compte OneClick du membre
 * @param firstName   prénom (nullable si user introuvable)
 * @param lastName    nom
 * @param email       email
 * @param phone       téléphone
 * @param memberType  type de membre (nullable)
 * @param status      statut de la membership ({@code active})
 * @param joinedAt    date d'activation (nullable)
 */
public record TenantMemberDto(
    UUID userId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String memberType,
    String status,
    Instant joinedAt
) {
}
