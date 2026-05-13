package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promo_notification_requests")
public class PromoNotificationRequest extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "offer_id")
    private UUID offerId;

    @Column(nullable = false)
    private String title;

    @Column private String body;

    @Column(nullable = false, length = 64)
    private String segment = "all";

    @Column(nullable = false, length = 32)
    private String status = "pending";

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "push_sent_at")
    private Instant pushSentAt;

    @Column(name = "push_sent_count")
    private Integer pushSentCount;

    @Column(name = "push_error")
    private String pushError;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getRestaurantId() { return restaurantId; }
    public void setRestaurantId(UUID v) { this.restaurantId = v; }
    public UUID getOfferId() { return offerId; }
    public void setOfferId(UUID v) { this.offerId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getBody() { return body; }
    public void setBody(String v) { this.body = v; }
    public String getSegment() { return segment; }
    public void setSegment(String v) { this.segment = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID v) { this.requestedBy = v; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID v) { this.reviewedBy = v; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant v) { this.reviewedAt = v; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String v) { this.rejectionReason = v; }
    public Instant getPushSentAt() { return pushSentAt; }
    public void setPushSentAt(Instant v) { this.pushSentAt = v; }
    public Integer getPushSentCount() { return pushSentCount; }
    public void setPushSentCount(Integer v) { this.pushSentCount = v; }
    public String getPushError() { return pushError; }
    public void setPushError(String v) { this.pushError = v; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant v) { this.deletedAt = v; }
}
