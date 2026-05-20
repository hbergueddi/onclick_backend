package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO d'entrée pour {@code POST /api/loyalty/snap2earn}.
 *
 * <p>Porté depuis l'Edge Function Supabase legacy {@code snap2earn} :
 * un staff scanne un ticket pour son client, on calcule les points via
 * {@link GainRuleDto} du restaurant (fallback rate par défaut si pas de règle),
 * puis on insère la transaction en réutilisant {@link LoyaltyEarnDto}.
 *
 * @param clientId       UUID du client crédité (FK users.id)
 * @param restaurantId   UUID du restaurant (FK restaurants.id)
 * @param amount         Montant TTC du ticket (MAD), {@code >= 0}
 * @param ticketRef      Référence ticket caisse (optionnel) — anti-doublon par (restaurantId, ticketRef)
 * @param photoUrl       URL Storage de la photo OCR (optionnel) — stockée dans {@code reason} pour audit
 */
public record Snap2EarnDto(
    @NotNull UUID clientId,
    @NotNull UUID restaurantId,
    @NotNull @DecimalMin("0.00") BigDecimal amount,
    @Size(min = 1, max = 64) String ticketRef,
    @Size(min = 1, max = 512) String photoUrl
) {
}
