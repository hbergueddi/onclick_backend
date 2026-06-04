package com.onesley.oneclick.modules.family.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Projection Spring Data — une ligne d'historique de points fidélité d'un proche, enrichie
 * du nom de son restaurant.
 *
 * <h3>Modulith — pourquoi une interface + nativeQuery ?</h3>
 * <p>Les entités {@code LoyaltyTransaction}/{@code LoyaltyAccount} vivent dans
 * {@code modules.loyalty.internal} et l'entité {@code Restaurant} dans
 * {@code modules.restaurant.internal} — aucune n'est importable depuis {@code modules.family}
 * (CLOSED). On reste donc au niveau SQL (noms de tables) via une {@code nativeQuery} dans
 * {@link PccFamilyMemberRepository#pointsHistoryByClientInTenant} ; le binding est assuré
 * par cette projection (getters {@code get<ColumnAlias>}, alias snake_case → camelCase
 * automatique). Même pattern que {@code LoyaltyAccountWithRestaurantView} (P2.c).</p>
 *
 * <p>Mapping legacy {@code loyalty_points} → modèle comptable Spring :
 * <ul>
 *   <li>{@code points} ← {@code loyalty_transactions.points} (signé : earn &gt; 0, spend &lt; 0).</li>
 *   <li>{@code amountTtc} ← {@code loyalty_transactions.amount} (montant TTC du ticket à l'origine).</li>
 *   <li>{@code earnedAt} ← {@code loyalty_transactions.created_at}.</li>
 *   <li>{@code remainingPoints} ← {@code loyalty_accounts.balance} (solde courant du compte —
 *       le legacy traçait un {@code remaining_points} FIFO par ligne, non tenu dans le modèle
 *       comptable Spring ; le solde du compte est l'analogue correct du « restant »).</li>
 * </ul></p>
 */
public interface PccFamilyPointsHistoryView {
    UUID getId();
    UUID getRestaurantId();
    String getRestaurantName();
    Integer getPoints();
    BigDecimal getAmountTtc();
    String getReason();
    Instant getEarnedAt();
    Integer getRemainingPoints();
    Instant getExpiresAt();
}
