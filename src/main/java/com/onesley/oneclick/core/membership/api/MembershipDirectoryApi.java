package com.onesley.oneclick.core.membership.api;

import java.util.List;
import java.util.UUID;

/**
 * Port de lecture des appartenances (memberships) d'un client à des programmes (tenants).
 *
 * <p>Contrat typé exposé hors module ({@code core.membership} OPEN), consommé par
 * {@code security} (pliage des authorities — P1) et par les modules business pour l'ABAC de
 * périmètre (« cet utilisateur est-il membre actif du tenant de cette ressource ? »), en
 * remplacement du scoping dispersé par {@code users.tenant_id}.
 *
 * <p>« Actif » = {@code status = 'active'} ET {@code deleted_at IS NULL}.
 */
public interface MembershipDirectoryApi {

    /** Projection légère d'une membership active (pas d'exposition de l'entité JPA). */
    record Membership(UUID tenantId, String memberType, String status) {}

    /** Vrai si l'utilisateur a une membership <b>active</b> dans ce tenant. Faux si args null. */
    boolean isActiveMember(UUID userId, UUID tenantId);

    /** Memberships <b>actives</b> de l'utilisateur (vide si aucune / userId null). */
    List<Membership> activeMemberships(UUID userId);

    /** Tenants des memberships <b>actives</b> de l'utilisateur (vide si aucune / userId null). */
    List<UUID> activeTenantIds(UUID userId);
}
