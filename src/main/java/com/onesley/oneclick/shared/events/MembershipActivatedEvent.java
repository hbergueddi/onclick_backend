package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand une membership programme devient (ou redevient) <b>active</b>
 * — création ou réactivation via {@code MembershipInviteService.invite} (P2).
 *
 * <p>Consommé par {@code security} ({@code MembershipCacheEvictionListener}) pour
 * <b>évincer le cache {@code userDetails}</b> de l'utilisateur : ses autorités
 * programme (pliage des memberships, P1) changent, donc son {@code OneClickUserDetails}
 * mis en cache est périmé. Découplage via {@code shared} (module OPEN) → pas de
 * dépendance {@code membership → security} (qui créerait un cycle, {@code security}
 * dépendant déjà de {@code membership.api} pour le pliage).</p>
 *
 * @param userId     membre dont la membership vient d'être activée (cible de l'éviction)
 * @param tenantId   tenant de la membership
 * @param occurredAt horodatage
 */
public record MembershipActivatedEvent(
    UUID userId,
    UUID tenantId,
    Instant occurredAt
) {
}
