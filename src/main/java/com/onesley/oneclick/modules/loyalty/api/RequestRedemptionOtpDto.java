package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corps de {@code POST /api/loyalty/redemption-otp/request} (Gap #2).
 *
 * @param clientId    client qui recevra le code in-app
 * @param restaurantId restaurant où s'effectue la conversion
 * @param points      points à convertir (déclenche le code si > seuil)
 * @param montant     montant du ticket (MAD)
 * @param discountDh  réduction estimée (MAD) — affichée dans la notification
 */
public record RequestRedemptionOtpDto(
    @NotNull UUID clientId,
    @NotNull UUID restaurantId,
    @NotNull @Min(1) Integer points,
    @NotNull @DecimalMin("0.00") BigDecimal montant,
    @NotNull @DecimalMin("0.00") BigDecimal discountDh
) {
}
