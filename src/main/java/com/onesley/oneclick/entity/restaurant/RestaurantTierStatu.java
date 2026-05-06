package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_tier_status} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "restaurant_tier_status")
public class RestaurantTierStatu extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "current_tier_id")
    private UUID currentTierId;

    @Column(name = "current_tier_slug", nullable = false)
    private String currentTierSlug;

    @Column(name = "monthly_ca", nullable = false)
    private BigDecimal monthlyCa;

    @Column(name = "tier_achieved_at")
    private Instant tierAchievedAt;

    @Column(name = "grace_expires_at")
    private Instant graceExpiresAt;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    protected RestaurantTierStatu() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getCurrentTierId() { return currentTierId; }
    public String getCurrentTierSlug() { return currentTierSlug; }
    public BigDecimal getMonthlyCa() { return monthlyCa; }
    public Instant getTierAchievedAt() { return tierAchievedAt; }
    public Instant getGraceExpiresAt() { return graceExpiresAt; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
}
