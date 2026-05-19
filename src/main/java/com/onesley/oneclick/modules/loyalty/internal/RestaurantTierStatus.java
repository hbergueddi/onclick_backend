package com.onesley.oneclick.modules.loyalty.internal;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
    @Setter @Size(max = 64) @NotBlank private String currentTier = "Standard";

    @Column(name = "points_earned", nullable = false)
    @Setter private Integer pointsEarned = 0;

    @Column(name = "last_evaluated_at", nullable = false)
    @Setter @NotNull private Instant lastEvaluatedAt = Instant.now();
}
