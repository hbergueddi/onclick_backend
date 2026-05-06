package com.onesley.oneclick.entity.restaurant;

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
 * Entité {@code public.restaurant_expired_pool} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "restaurant_expired_pool")
@EntityListeners(AuditingEntityListener.class)
public class RestaurantExpiredPool {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "expired_point_id", nullable = false)
    private UUID expiredPointId;

    @Column(name = "points", nullable = false)
    private Integer points;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consolidated_at")
    private Instant consolidatedAt;

    @Column(name = "restitution_id")
    private UUID restitutionId;

    protected RestaurantExpiredPool() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getClientId() { return clientId; }
    public UUID getExpiredPointId() { return expiredPointId; }
    public Integer getPoints() { return points; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConsolidatedAt() { return consolidatedAt; }
    public UUID getRestitutionId() { return restitutionId; }
}
