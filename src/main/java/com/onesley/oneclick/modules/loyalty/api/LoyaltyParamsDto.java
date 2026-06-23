package com.onesley.oneclick.modules.loyalty.api;

import java.math.BigDecimal;

/**
 * Paramètres fidélité <b>effectifs</b> d'un client pour un restaurant — alimente
 * l'écran Pocket « Comment ça marche / Vos avantages » + {@code ConversionGuide}
 * (legacy {@code OneClickVault.tsx}, bloc {@code loyaltyParams}).
 *
 * <p>« Effectif » = valeur de base (règle du restaurant) à laquelle on a appliqué
 * le bonus du palier ({@code tier}) du client. Le calcul de fusion est fait
 * côté serveur (parité {@code useLoyaltyParams.ts}) pour que le front consomme
 * directement sans ré-implémenter la priorité base → palier.
 *
 * <h3>Sources DB des 4 valeurs clés</h3>
 * <ul>
 *   <li>{@code conversionRatePct} — {@code gain_rules.conversion_rate} (par restaurant, V13),
 *       multiplié par {@code (1 + tierBonusPct/100)}. % MAD → points. Défaut {@code 0.10}
 *       ({@code GainRule.conversionRate} par défaut) si aucune règle.</li>
 *   <li>{@code pointValueMad} — {@code loyalty_rules.point_value} (source de vérité hors
 *       {@code gain_rules}). Défaut {@code 1.0} ({@code GainRule.DEFAULT_POINT_VALUE_MAD} :
 *       1 pt = 1 MAD).</li>
 *   <li>{@code benefitDurationDays} — {@code loyalty_rules.expires_after_days}. Défaut {@code 365}
 *       ({@code LoyaltyRule.expiresAfterDays} par défaut).</li>
 *   <li>{@code tierName} / {@code tierBonusPct} — palier résolu par {@code LoyaltyTierResolver}
 *       (table {@code tiers} du tenant, sinon fallback canonique) sur le total des soldes
 *       du client dans ce tenant ; {@code bonus_percent} de la ligne {@code tiers}.</li>
 * </ul>
 *
 * <p>Pas de migration : LECTURE de colonnes existantes ({@code gain_rules},
 * {@code loyalty_rules}, {@code tiers}).</p>
 *
 * @param conversionRatePct  taux de conversion effectif (fraction, ex. {@code 0.11} = 11 % de cashback) — bonus palier inclus
 * @param pointValueMad      valeur monétaire d'un point (1 pt = N MAD)
 * @param benefitDurationDays durée de validité des points gagnés (jours)
 * @param tierName           nom du palier courant du client ({@code null} si le tenant n'a pas de palier configuré et points = 0 fallback Connaisseur côté resolver) — jamais null en pratique (resolver renvoie un plancher)
 * @param tierBonusPct       bonus du palier en pourcentage (ex. {@code 10.00}) ; {@code 0} si aucun bonus
 */
public record LoyaltyParamsDto(
    BigDecimal conversionRatePct,
    BigDecimal pointValueMad,
    int benefitDurationDays,
    String tierName,
    BigDecimal tierBonusPct
) {
}
