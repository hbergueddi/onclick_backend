package com.onesley.oneclick.core.membership.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Port de lecture des appartenances (memberships) d'un client à des programmes (tenants).
 *
 * <p>Contrat typé exposé hors module ({@code core.membership} OPEN), consommé par
 * {@code security} (pliage des authorities — P1 {@link #authoritiesFor(UUID)}) et par les modules
 * business pour l'ABAC de périmètre (« cet utilisateur est-il membre actif du tenant de cette
 * ressource ? »), en remplacement du scoping dispersé par {@code users.tenant_id}.
 *
 * <p>« Actif » = {@code status = 'active'} ET {@code deleted_at IS NULL}.
 */
public interface MembershipDirectoryApi {

    /** Projection légère d'une membership active (pas d'exposition de l'entité JPA). */
    record Membership(UUID tenantId, String memberType, String status) {}

    /**
     * Projection <b>enrichie</b> d'une membership active : tenant résolu (slug + nom). Sert la
     * « révélation » des espaces programme côté front (Win) — qui n'a que le slug pour décider quels
     * espaces afficher (PCC/HOMU/...) et quel thème contextuel activer.
     */
    record MembershipView(UUID tenantId, String tenantSlug, String tenantName, String memberType, String status) {}

    /** Vrai si l'utilisateur a une membership <b>active</b> dans ce tenant. Faux si args null. */
    boolean isActiveMember(UUID userId, UUID tenantId);

    /** Memberships <b>actives</b> de l'utilisateur (vide si aucune / userId null). */
    List<Membership> activeMemberships(UUID userId);

    /** Memberships <b>actives</b> enrichies (tenant slug/nom) de l'utilisateur — pour le reveal. */
    List<MembershipView> activeMembershipViews(UUID userId);

    /** Tenants des memberships <b>actives</b> de l'utilisateur (vide si aucune / userId null). */
    List<UUID> activeTenantIds(UUID userId);

    /**
     * Authorities ({@code VERB:RESOURCE}) octroyées par les memberships <b>actives</b> de
     * l'utilisateur, via le {@code role} programme de chaque membership. Pliées dans le security
     * context par {@code OneClickUserDetailsService} (modèle « l'invitation accorde les
     * permissions » — hasAuthority strict). Vide si aucune membership / role / userId null.
     */
    Set<String> authoritiesFor(UUID userId);
}
