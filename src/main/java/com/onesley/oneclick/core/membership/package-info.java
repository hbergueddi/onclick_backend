/**
 * Module {@code core/membership} — appartenance ADDITIVE d'un client à un programme (tenant).
 *
 * <h3>Raison d'être</h3>
 * <p>Politique « 1 seul compte OneClick + memberships additives » : un client a UNE identité
 * OneClick (tenant <i>home</i> = oneclick) et peut être <b>membre</b> de N programmes (PCC, HOMU,
 * futurs) via {@code tenant_memberships}. L'accès au contenu d'un programme découle de la
 * membership (P1 : pliage des authorities ; P2 : invitation par l'admin du tenant).</p>
 *
 * <h3>Placement en {@code core} (et non {@code modules})</h3>
 * <p>La membership est un concern transverse consommé à la fois par {@code security} (chargement
 * des authorities) et par les modules business ({@code resource_booking}, {@code family},
 * {@code feedback}…) pour l'ABAC de périmètre. La placer en {@code core} (comme {@code core.identity}
 * / {@code core.tenant}) évite l'inversion de couche {@code core → modules}.</p>
 *
 * <h3>API exposée</h3>
 * <ul>
 *   <li>{@code api/MembershipDirectoryApi} — port de lecture (isActiveMember / activeMemberships /
 *       activeTenantIds), consommable hors module ; aucune entité JPA exposée.</li>
 *   <li>{@code internal/TenantMembership} + repo + {@code MembershipService} (impl du port) restent privés.</li>
 * </ul>
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    id = "core.membership",
    displayName = "core/membership"
)
package com.onesley.oneclick.core.membership;

import org.springframework.modulith.ApplicationModule;
