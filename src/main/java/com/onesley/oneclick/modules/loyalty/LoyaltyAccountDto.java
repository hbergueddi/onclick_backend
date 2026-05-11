package com.onesley.oneclick.modules.loyalty;

import java.time.Instant;
import java.util.UUID;

public record LoyaltyAccountDto(
    UUID id,
    UUID clientId,
    UUID restaurantId,
    UUID tierId,
    String tierName,
    Integer balance,
    Instant createdAt
) {
    public static LoyaltyAccountDto from(LoyaltyAccount a) {
        return new LoyaltyAccountDto(
            a.getId(), a.getClientId(), a.getRestaurantId(),
            a.getTierId(),
            a.getTier() != null ? a.getTier().getName() : null,
            a.getBalance(),
            a.getCreatedAt()
        );
    }
}
