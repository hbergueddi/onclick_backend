package com.onesley.oneclick.modules.loyalty.internal;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "restaurant_tier_status")
@Getter
public class RestaurantTierStatus {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    @Setter private UUID restaurantId;

    @Column(name = "current_tier", nullable = false, length = 64)
    @Setter private String currentTier = "Standard";

    @Column(name = "points_earned", nullable = false)
    @Setter private Integer pointsEarned = 0;

    @Column(name = "last_evaluated_at", nullable = false)
    @Setter private Instant lastEvaluatedAt = Instant.now();
}
