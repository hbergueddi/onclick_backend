package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO pour {@code POST /api/loyalty/gift} — Sprint G.5 (port EF gift-points).
 *
 * <p>Un client peut offrir des points à un ami pour un restaurant donné.
 * Le service débite le solde du sender et crédite celui du receiver,
 * dans la même transaction atomique.
 *
 * <p>Contraintes métier :
 * <ul>
 *   <li>{@code points} entre 10 et 200 par défaut (cf legacy max/jour)</li>
 *   <li>Sender et receiver doivent avoir un compte loyalty sur ce restaurant</li>
 *   <li>Solde sender suffisant (BadRequestException sinon)</li>
 *   <li>Le sender ne peut pas s'offrir à lui-même (sender != receiver)</li>
 * </ul>
 */
public record GiftPointsDto(
    @NotNull UUID receiverId,
    @NotNull UUID restaurantId,
    @NotNull @Min(10) @Max(200) Integer points,
    @Size(max = 200) @Size(min = 1, max = 1024) String message
) {
}
