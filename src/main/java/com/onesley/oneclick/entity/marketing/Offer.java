package com.onesley.oneclick.entity.marketing;

import com.onesley.oneclick.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.offers} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : audit niveau 1 (4 colonnes).
 */
@Entity
@Table(name = "offers")
public class Offer extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "image")
    private String image;

    @Column(name = "pts", nullable = false)
    private Integer pts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "segments", nullable = false, columnDefinition = "text[]")
    private List<String> segments = new ArrayList<>();

    @Column(name = "push_notify", nullable = false)
    private Boolean pushNotify;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "tenant_id")
    private UUID tenantId;

    protected Offer() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getImage() { return image; }
    public Integer getPts() { return pts; }
    public Instant getExpiresAt() { return expiresAt; }
    public Boolean getIsActive() { return isActive; }
    public List<String> getSegments() { return segments; }
    public Boolean getPushNotify() { return pushNotify; }
    public UUID getCampaignId() { return campaignId; }
    public Instant getStartsAt() { return startsAt; }
    public UUID getTenantId() { return tenantId; }
}
