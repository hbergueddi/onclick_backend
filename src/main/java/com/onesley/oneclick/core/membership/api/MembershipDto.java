package com.onesley.oneclick.core.membership.api;

import java.util.UUID;

/**
 * Projection d'une membership renvoyée après invitation (P2). PII-free (pas de nom/email :
 * l'admin connaît déjà l'invité ; l'enrichissement nom viendra avec la liste P3).
 *
 * @param id          identifiant de la membership
 * @param userId      compte OneClick membre
 * @param tenantId    tenant du programme
 * @param memberType  type de membre (nullable)
 * @param status      statut ({@code active})
 * @param newAccount  vrai si l'invitation a CRÉÉ le compte OneClick (→ email d'activation envoyé)
 */
public record MembershipDto(
    UUID id,
    UUID userId,
    UUID tenantId,
    String memberType,
    String status,
    boolean newAccount
) {
}
