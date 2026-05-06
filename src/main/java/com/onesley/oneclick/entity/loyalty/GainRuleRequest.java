package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.gain_rule_requests} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "gain_rule_requests")
public class GainRuleRequest extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "taux_conversion", nullable = false)
    private BigDecimal tauxConversion;

    @Column(name = "min_ticket", nullable = false)
    private BigDecimal minTicket;

    @Column(name = "max_points_par_ticket", nullable = false)
    private Integer maxPointsParTicket;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected GainRuleRequest() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public BigDecimal getTauxConversion() { return tauxConversion; }
    public BigDecimal getMinTicket() { return minTicket; }
    public Integer getMaxPointsParTicket() { return maxPointsParTicket; }
    public String getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
}
