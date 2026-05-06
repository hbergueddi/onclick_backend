package com.onesley.oneclick.entity.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.loyalty_points} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : entité simple.
 */
@Entity
@Table(name = "loyalty_points")
public class LoyaltyPoint {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @NotNull
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotNull
    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "reason")
    private String reason;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    @Column(name = "amount_ttc")
    private BigDecimal amountTtc;

    @Column(name = "credited_by")
    private UUID creditedBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "remaining_points")
    private Integer remainingPoints;

    @Column(name = "notified_7d_at")
    private Instant notified7dAt;

    @Column(name = "notified_1d_at")
    private Instant notified1dAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "modified_by")
    private UUID modifiedBy;

    protected LoyaltyPoint() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getRestaurantId() { return restaurantId; }
    public Integer getPoints() { return points; }
    public String getReason() { return reason; }
    public Instant getEarnedAt() { return earnedAt; }
    public BigDecimal getAmountTtc() { return amountTtc; }
    public UUID getCreditedBy() { return creditedBy; }
    public Instant getExpiresAt() { return expiresAt; }
    public Integer getRemainingPoints() { return remainingPoints; }
    public Instant getNotified7dAt() { return notified7dAt; }
    public Instant getNotified1dAt() { return notified1dAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getModifiedBy() { return modifiedBy; }
}
