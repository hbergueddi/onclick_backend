package com.onesley.oneclick.loyalty;

import java.time.Instant;
import java.util.UUID;

public record LoyaltyAccountDto(
    UUID id,
    UUID clientId,
    UUID restaurantId,
    UUID tierId,
    Integer balance,
    Instant createdAt
) {
    public static LoyaltyAccountDto from(LoyaltyAccount a) {
        // tierName retiré : suppression de @ManyToOne Tier dans LoyaltyAccount
        // (cross-aggregate ref). Si besoin du nom, query séparée par tierId.
        return new LoyaltyAccountDto(
            a.getId(), a.getClientId(), a.getRestaurantId(),
            a.getTierId(),
            a.getBalance(),
            a.getCreatedAt()
        );
    }
}
