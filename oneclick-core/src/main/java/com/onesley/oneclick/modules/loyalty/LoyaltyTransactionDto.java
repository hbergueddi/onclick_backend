package com.onesley.oneclick.modules.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoyaltyTransactionDto(
    UUID id,
    UUID accountId,
    String type,
    Integer points,
    BigDecimal amount,
    String reason,
    Instant expiresAt,
    Instant createdAt,
    UUID createdById
) {
    public static LoyaltyTransactionDto from(LoyaltyTransaction t) {
        return new LoyaltyTransactionDto(
            t.getId(), t.getAccountId(), t.getType(), t.getPoints(),
            t.getAmount(), t.getReason(), t.getExpiresAt(),
            t.getCreatedAt(), t.getCreatedById()
        );
    }
}
