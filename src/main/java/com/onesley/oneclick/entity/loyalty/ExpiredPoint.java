package com.onesley.oneclick.entity.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.expired_points} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "expired_points")
@EntityListeners(AuditingEntityListener.class)
public class ExpiredPoint {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "original_point_id", nullable = false)
    private UUID originalPointId;

    @Column(name = "points_expired", nullable = false)
    private Integer pointsExpired;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    @Column(name = "expired_at", nullable = false)
    private Instant expiredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExpiredPoint() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getOriginalPointId() { return originalPointId; }
    public Integer getPointsExpired() { return pointsExpired; }
    public Instant getEarnedAt() { return earnedAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
