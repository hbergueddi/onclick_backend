package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_restitutions} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "restaurant_restitutions")
public class RestaurantRestitution extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotNull
    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @NotNull
    @Column(name = "total_points", nullable = false)
    private Integer totalPoints;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "notes")
    private String notes;

    protected RestaurantRestitution() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public Integer getTotalPoints() { return totalPoints; }
    public String getStatus() { return status; }
    public UUID getProcessedBy() { return processedBy; }
    public Instant getProcessedAt() { return processedAt; }
    public String getNotes() { return notes; }
}
