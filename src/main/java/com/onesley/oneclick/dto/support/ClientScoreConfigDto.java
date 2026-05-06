package com.onesley.oneclick.dto.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code client_score_config} (généré par scripts/scaffold-jpa.mjs).
 */
public record ClientScoreConfigDto(
    UUID id,
    Integer minReservations,
    BigDecimal seuilExcellent,
    BigDecimal seuilFiable,
    BigDecimal seuilMoyen,
    Instant updatedAt,
    BigDecimal scoreInitial,
    BigDecimal penaliteNoShow,
    Integer honoreesPourRemonter,
    BigDecimal gainParPalier,
    Integer fenetreMois
) {
}
