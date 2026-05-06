package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code admin_wallet_transactions} (généré par scripts/scaffold-jpa.mjs).
 */
public record AdminWalletTransactionDto(
    UUID id,
    UUID adminId,
    UUID restaurantId,
    Integer amount,
    String reason,
    String details,
    Instant createdAt,
    Instant expiresAt,
    Integer remainingAmount
) {
}
