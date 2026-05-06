package com.onesley.oneclick.dto.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code redemption_otp_requests} (généré par scripts/scaffold-jpa.mjs).
 */
public record RedemptionOtpRequestDto(
    UUID id,
    Instant createdAt,
    Instant expiresAt,
    UUID clientId,
    UUID restaurantId,
    UUID staffId,
    Integer pointsRequested,
    BigDecimal ticketMontant,
    BigDecimal estimatedDiscountDh,
    String codeHash,
    String status,
    Integer attempts,
    Instant consumedAt,
    String consumedForTicketRef
) {
}
