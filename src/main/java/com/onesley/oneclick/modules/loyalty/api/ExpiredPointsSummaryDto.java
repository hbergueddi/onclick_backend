package com.onesley.oneclick.modules.loyalty.api;

import java.util.List;

/**
 * DTO public — résumé des points expirés d'un client (toutes comptes confondus).
 *
 * <p>{@code totalExpired} est exprimé en valeur absolue (points positifs)
 * pour faciliter l'affichage, même si la colonne {@code points} en DB est négative
 * pour les transactions de type {@code expire}.
 */
public record ExpiredPointsSummaryDto(
    int totalExpired,
    List<LoyaltyTransactionDto> transactions
) {
}
