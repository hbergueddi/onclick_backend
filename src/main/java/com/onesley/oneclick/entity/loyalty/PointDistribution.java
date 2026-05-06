package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.entity.shared.DistributionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.point_distributions} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "point_distributions")
@EntityListeners(AuditingEntityListener.class)
public class PointDistribution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source_type", nullable = false, columnDefinition = "distribution_source")
    private DistributionSource sourceType;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "segment")
    private String segment;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "offer_id")
    private UUID offerId;

    @Column(name = "status", nullable = false)
    private String status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PointDistribution() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getAdminId() { return adminId; }
    public DistributionSource getSourceType() { return sourceType; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getClientId() { return clientId; }
    public String getSegment() { return segment; }
    public Integer getPoints() { return points; }
    public String getReason() { return reason; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getOfferId() { return offerId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
