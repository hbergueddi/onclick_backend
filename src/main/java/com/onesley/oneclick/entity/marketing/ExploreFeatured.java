package com.onesley.oneclick.entity.marketing;

import com.onesley.oneclick.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.explore_featured} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : audit niveau 1 (4 colonnes).
 */
@Entity
@Table(name = "explore_featured")
public class ExploreFeatured extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "label")
    private String label;

    @Column(name = "notes")
    private String notes;

    protected ExploreFeatured() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Integer getPosition() { return position; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Boolean getIsActive() { return isActive; }
    public String getLabel() { return label; }
    public String getNotes() { return notes; }
}
