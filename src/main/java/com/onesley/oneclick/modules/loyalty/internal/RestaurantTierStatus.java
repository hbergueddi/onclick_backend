package com.onesley.oneclick.modules.loyalty.internal;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_tier_status")
public class RestaurantTierStatus {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private UUID restaurantId;

    @Column(name = "current_tier", nullable = false, length = 64)
    private String currentTier = "Standard";

    @Column(name = "points_earned", nullable = false)
    private Integer pointsEarned = 0;

    @Column(name = "last_evaluated_at", nullable = false)
    private Instant lastEvaluatedAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public void setRestaurantId(UUID v) { this.restaurantId = v; }
    public String getCurrentTier() { return currentTier; }
    public void setCurrentTier(String v) { this.currentTier = v; }
    public Integer getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(Integer v) { this.pointsEarned = v; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(Instant v) { this.lastEvaluatedAt = v; }
}
