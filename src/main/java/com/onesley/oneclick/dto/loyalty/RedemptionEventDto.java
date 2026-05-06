package com.onesley.oneclick.dto.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code redemption_events} (généré par scripts/scaffold-jpa.mjs).
 */
public record RedemptionEventDto(
    UUID id,
    Instant createdAt,
    UUID clientId,
    UUID restaurantId,
    UUID scannedBy,
    String ticketRef,
    BigDecimal ticketMontant,
    Integer pointsRedeemed,
    BigDecimal discountDh,
    String clientTier,
    BigDecimal effectivePointValueMad,
    Boolean accepted,
    String rejectionReason,
    Boolean flagRatioHigh,
    Boolean flagDailyNearCap,
    Boolean flagFirstRedemption,
    Boolean flagLargeAbsolute
) {
}
