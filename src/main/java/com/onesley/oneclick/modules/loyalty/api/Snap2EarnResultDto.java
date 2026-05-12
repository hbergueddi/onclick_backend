package com.onesley.oneclick.modules.loyalty.api;

import java.util.UUID;

/**
 * DTO de sortie pour {@code POST /api/loyalty/snap2earn}.
 *
 * @param pointsEarned     Points crédités effectivement (peut être 0 si {@code amount < minAmount} du gain rule)
 * @param accountBalance   Solde courant après crédit
 * @param transactionId    UUID de la transaction {@code loyalty_transactions} créée
 * @param gainRuleApplied  Nom court de la règle appliquée ({@code restaurant} si {@link GainRuleDto} trouvé, {@code default} sinon)
 */
public record Snap2EarnResultDto(
    Integer pointsEarned,
    Integer accountBalance,
    UUID transactionId,
    String gainRuleApplied
) {
}
