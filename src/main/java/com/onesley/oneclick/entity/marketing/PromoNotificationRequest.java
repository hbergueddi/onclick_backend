package com.onesley.oneclick.entity.marketing;

import com.onesley.oneclick.audit.TimestampedEntity;
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
 * Entité {@code public.promo_notification_requests} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "promo_notification_requests")
public class PromoNotificationRequest extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "message")
    private String message;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "target_segments", nullable = false, columnDefinition = "text[]")
    private List<String> targetSegments = new ArrayList<>();

    @Column(name = "admin_note")
    private String adminNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "push_sent_at")
    private Instant pushSentAt;

    @Column(name = "push_sent_count")
    private Integer pushSentCount;

    @Column(name = "push_error")
    private String pushError;

    protected PromoNotificationRequest() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getOfferId() { return offerId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public List<String> getTargetSegments() { return targetSegments; }
    public String getAdminNote() { return adminNote; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getPushSentAt() { return pushSentAt; }
    public Integer getPushSentCount() { return pushSentCount; }
    public String getPushError() { return pushError; }
}
