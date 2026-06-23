/**
 * Module {@code modules/restaurant_referral} — Parrainage RESTAURANT-à-RESTAURANT (owner → owner).
 *
 * <p>Un restaurateur (owner d'un resto EXISTANT) parraine un NOUVEAU resto. À l'activation du code
 * (immédiat), le <b>PARRAIN seul</b> reçoit des <b>POINTS de fidélité</b> (le filleul ne reçoit rien ;
 * aucun flux financier/commission). Scope : <b>mono-tenant « oneclick »</b> (pas cross-tenant), PAS
 * de plafond.
 *
 * <p>Modèle DB isolé de {@code referrals} (parrainage CLIENT, module {@code social}) : ici le
 * parrainage porte un resto parrain + (à l'activation) un resto filleul, et matérialise la
 * récompense ({@code reward_points}/{@code rewarded_at}).
 *
 * <h3>Pourquoi un module dédié (pas dans {@code modules/restaurant})</h3>
 * <p>Cette feature DOIT créditer la fidélité du parrain — communication sortante vers le module
 * {@code loyalty}. La placer dans {@code modules/restaurant} (catalogue) introduirait soit un
 * couplage business↔business, soit un mélange de responsabilités. Un module CLOSED dédié garde la
 * frontière propre : il ne dépend que de {@code core}/{@code security}/{@code realtime} et publie
 * un event {@code shared.events} consommé par {@code loyalty} — exactement le pattern du parrainage
 * CLIENT ({@code social}) et du module {@code feedback}.
 *
 * <h3>Modulith CLOSED — discipline architecturale</h3>
 * <ul>
 *   <li>{@code api/} — DTOs exposés hors module ({@code RestaurantReferralDtos}).</li>
 *   <li>{@code internal/} — entité JPA {@code RestaurantReferral} + repo + service + STOMP publisher
 *       + listener loyalty.</li>
 *   <li>Lectures cross-module (pattern « hybride core-only ») :
 *     <ul>
 *       <li>tenant du resto + owner-scope ({@code restaurant_staffs.role_code='owner'} non supprimé)
 *           → <b>read-view SQL native</b> dans le repo (tables {@code restaurants}/{@code restaurant_staffs}
 *           en {@code nativeQuery}) — invariant « 0 dépendance business↔business », jamais d'import
 *           des modules {@code restaurant}/{@code loyalty} ;</li>
 *       <li>slug du tenant « oneclick » (scope) → port typé {@code core.tenant.api.TenantDirectoryApi}
 *           (core.tenant OPEN).</li>
 *     </ul>
 *   </li>
 *   <li>Communication out :
 *     <ul>
 *       <li>event {@link com.onesley.oneclick.shared.events.RestaurantReferralActivatedEvent}
 *           (in-process via Modulith) consommé par {@code loyalty}
 *           ({@code RestaurantReferralRewardListener}) qui crédite les points du parrain ;</li>
 *       <li>temps réel {@code RestaurantReferralDashboardPublisher} (STOMP, fingerprint) :
 *           {@code /topic/admin/restaurant-referrals} (dashboard admin des parrainages).</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.restaurant_referral",
    displayName = "modules/restaurant_referral",
    allowedDependencies = {"core.identity", "core.tenant", "audit", "exception", "security", "realtime", "shared"}
)
package com.onesley.oneclick.modules.restaurant_referral;

import org.springframework.modulith.ApplicationModule;
