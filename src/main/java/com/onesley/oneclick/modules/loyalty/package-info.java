/**
 * Module {@code modules/loyalty} — fidélité (§6).
 *
 * <h3>Modèle comptable propre</h3>
 * <p>Vs legacy {@code loyalty_points} (1 row par mouvement, balance non recalculable
 * sans agrégation lourde), on a maintenant :
 * <ul>
 *   <li>{@code loyalty_accounts} — solde courant par (client × restaurant)</li>
 *   <li>{@code loyalty_transactions} — mouvements typés (earn/spend/expire/gift/adjust)</li>
 *   <li>{@code redemptions} — utilisations de points avec OTP validation</li>
 *   <li>{@code loyalty_rules} — règles par restaurant (conversion rate, plafonds)</li>
 *   <li>{@code tiers} — niveaux (Ruby, Sapphire, Emeraude...) par tenant</li>
 * </ul>
 *
 * <h3>Modulith CLOSED — discipline architecturale</h3>
 * <ul>
 *   <li>{@code api/} — DTOs exposés hors module (LoyaltyAccountDto, LoyaltyTransactionDto, LoyaltyEarnDto)</li>
 *   <li>{@code internal/} — entities JPA + repos + service privé (ne sortent jamais du module)</li>
 *   <li>Communication out — events {@link com.onesley.oneclick.shared.events.LoyaltyEarnedEvent}
 *       et {@link com.onesley.oneclick.shared.events.LoyaltyRedeemedEvent} (in-process via Modulith)</li>
 * </ul>
 */
@ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.CLOSED, id = "modules.loyalty", displayName = "modules/loyalty")
package com.onesley.oneclick.modules.loyalty;

import org.springframework.modulith.ApplicationModule;
