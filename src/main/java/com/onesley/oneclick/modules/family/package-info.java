/**
 * Module {@code modules/family} — « Ma Famille » (PCC Lot 5).
 *
 * <p>Un membre (A) ajoute des proches (B) du <b>même tenant</b> à sa liste famille
 * (3 méthodes : email / code parrainage {@code OC-…} / téléphone), consulte le total
 * de points fidélité restants de chacun et l'historique complet des points d'un proche.
 * Limite 10 membres par caller. Relations unidirectionnelles A→B ; retrait permis des 2 côtés.
 *
 * <p>Port fidèle du legacy Supabase ({@code pcc_family_members} + 4 RPCs SECURITY DEFINER
 * {@code add/list/get_family_member_points_history/remove}). Différence senior : le scope
 * tenant est celui <b>du caller</b> (résolu via {@code UserDirectoryApi}), pas un UUID
 * palmeraie en dur → réutilisable par tout tenant whitelabel.
 *
 * <h3>Modulith CLOSED — discipline architecturale</h3>
 * <ul>
 *   <li>{@code api/} — DTOs exposés hors module ({@code PccFamilyDtos}).</li>
 *   <li>{@code internal/} — entité JPA {@code PccFamilyMember} + repo + service privé.</li>
 *   <li>Lectures cross-module (pattern P2 « hybride core-only ») :
 *     <ul>
 *       <li>users (résolution proche + noms/avatar/tenant) → contrat typé
 *           {@code core.identity.api.UserDirectoryApi} (core.identity est OPEN) ;</li>
 *       <li>points fidélité (total restant + historique) → <b>read-view SQL native</b>
 *           dans le repo (noms de tables {@code loyalty_accounts}/{@code loyalty_transactions}/
 *           {@code restaurants} en {@code nativeQuery}) — invariant « 0 dépendance
 *           business↔business », jamais d'import du module {@code loyalty} (pattern P2.c).</li>
 *     </ul>
 *   </li>
 *   <li>Communication out — event {@link com.onesley.oneclick.shared.events.FamilyMemberAddedEvent}
 *       (in-process via Modulith) consommé par {@code core.notification} pour notifier le proche.</li>
 * </ul>
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.family",
    displayName = "modules/family",
    allowedDependencies = {"core.identity", "audit", "exception", "shared", "security"}
)
package com.onesley.oneclick.modules.family;

import org.springframework.modulith.ApplicationModule;
