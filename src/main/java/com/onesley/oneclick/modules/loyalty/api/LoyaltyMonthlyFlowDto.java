package com.onesley.oneclick.modules.loyalty.api;

import java.time.LocalDate;

/**
 * Flux mensuels de points (Gap #9 — pilotage fidélité) — 1 ligne par mois sur 12 mois.
 *
 * <p>Port de la RPC legacy {@code get_loyalty_monthly_flows} : pour chaque mois,
 * points gagnés ({@code earn}), utilisés/échangés ({@code spend}) et expirés ({@code expire}).
 * Les compteurs {@code redeemed}/{@code expired} sont renvoyés en valeur ABSOLUE (positifs)
 * pour l'affichage du graphe (les transactions {@code spend}/{@code expire} ont points &lt; 0).
 *
 * @param monthStart     1er jour du mois (UTC)
 * @param pointsEarned   points gagnés ce mois (somme des {@code earn}, ≥ 0)
 * @param pointsRedeemed points utilisés ce mois (|somme des {@code spend}|, ≥ 0)
 * @param pointsExpired  points expirés ce mois (|somme des {@code expire}|, ≥ 0)
 */
public record LoyaltyMonthlyFlowDto(
    LocalDate monthStart,
    long pointsEarned,
    long pointsRedeemed,
    long pointsExpired
) {}
