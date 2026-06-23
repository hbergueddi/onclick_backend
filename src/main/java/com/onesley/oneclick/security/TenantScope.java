package com.onesley.oneclick.security;

import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Résout le périmètre tenant <b>visible</b> par le caller pour les listes/lectures <b>client-facing</b>,
 * via {@link MembershipDirectoryApi#visibleTenantIds(UUID)} = {tenant public « oneclick »} ∪ memberships
 * actives du caller.
 *
 * <p>Composant partagé (module {@code security}, OPEN — déjà dépendant de {@code core.membership.api}),
 * réutilisé par les modules client-facing (restaurant, promotion, event, resource_booking…) pour fermer
 * la <b>fuite de périmètre</b> : un client oneclick non-membre ne doit jamais voir les ressources d'un
 * programme (PCC/HOMU) dont il n'est pas membre.
 *
 * <p><b>ABAC</b> : un acteur <b>cross-tenant</b> (SUPERADMIN — identifié par {@code hasAuthority(
 * "VIEW:TENANTS")}, autorité que seul le SUPERADMIN détient, cf. {@code MemberCircleController}) n'est
 * PAS restreint : {@link #visibleTenantIdsOrNull()} renvoie {@code null} (= aucun filtre tenant).
 */
@Component
public class TenantScope {

    private final MembershipDirectoryApi membershipDirectory;

    public TenantScope(MembershipDirectoryApi membershipDirectory) {
        this.membershipDirectory = membershipDirectory;
    }

    /**
     * Set des tenants visibles par le caller courant, ou {@code null} si acteur cross-tenant
     * (SUPERADMIN) → l'appelant ne doit alors appliquer <b>aucun</b> filtre tenant. Le set inclut
     * toujours le tenant public « oneclick » (donc non-vide en pratique), même non authentifié.
     */
    public Set<UUID> visibleTenantIdsOrNull() {
        if (SecurityHelper.hasAuthority("VIEW:TENANTS")) {
            return null;
        }
        return membershipDirectory.visibleTenantIds(SecurityHelper.currentUserId());
    }

    /**
     * Périmètre de la <b>découverte générique publique</b> (catalogue Explore SANS tenant explicite) :
     * tenant public « oneclick » <b>UNIQUEMENT</b> pour un client (membre ou non), ou {@code null}
     * pour un acteur cross-tenant (SUPERADMIN, vue globale).
     *
     * <p>Différence clé avec {@link #visibleTenantIdsOrNull()} : ce dernier inclut les programmes du
     * membre ({@code oneclick ∪ memberships}) — adapté aux lectures par id / aux écrans <i>de</i>
     * programme (tenantId explicite). Mais la découverte GÉNÉRIQUE (Explore) ne doit JAMAIS faire
     * remonter les ressources d'un programme (Padel/Spa/Golf/Coiffeur/Palm Gym/Tennis/Foot/Basket
     * PCC…), <b>même pour un membre</b> : ce contenu est atteint via le <i>reveal</i> dédié
     * (PccHome / écran restaurants du programme, qui passe un tenantId explicite). Ferme la fuite
     * d'un tenant programme dans le catalogue grand public.
     *
     * <p>Fail-closed : si le tenant public est introuvable (misconfiguration) → ensemble <b>vide</b>
     * (catalogue vide) plutôt qu'aucun filtre.
     */
    public Set<UUID> publicCatalogScopeOrNull() {
        if (SecurityHelper.hasAuthority("VIEW:TENANTS")) {
            return null; // acteur cross-tenant (SUPERADMIN) — vue globale, pas de filtre
        }
        return membershipDirectory.publicTenantId()
            .map(Set::of)
            .orElseGet(Set::of); // public introuvable → vide (fail-closed)
    }

    /**
     * Vrai si le caller peut voir une ressource du tenant {@code tenantId} : acteur cross-tenant
     * (aucune restriction), ou {@code tenantId} dans son périmètre visible. Pour les lectures par id.
     */
    public boolean canSeeTenant(UUID tenantId) {
        Set<UUID> visible = visibleTenantIdsOrNull();
        return visible == null || (tenantId != null && visible.contains(tenantId));
    }
}
