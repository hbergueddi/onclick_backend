package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Système de notation client 0-5 — Sprint H.
 *
 * <p>3 colonnes parallèles (rating / visible_rating / pending_rating) pour gérer
 * la visibilité différée (cf feedback_delayed_visibility_rating.md).
 */
@Entity
@Table(name = "client_ratings")
public class ClientRating extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating = new BigDecimal("5.0");

    @Column(name = "visible_rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal visibleRating = new BigDecimal("5.0");

    @Column(name = "pending_rating", precision = 2, scale = 1)
    private BigDecimal pendingRating;

    @Column private String reason;

    @Column(precision = 2, scale = 1)
    private BigDecimal delta;

    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }
    public UUID getReservationId() { return reservationId; }
    public void setReservationId(UUID v) { this.reservationId = v; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal v) { this.rating = v; }
    public BigDecimal getVisibleRating() { return visibleRating; }
    public void setVisibleRating(BigDecimal v) { this.visibleRating = v; }
    public BigDecimal getPendingRating() { return pendingRating; }
    public void setPendingRating(BigDecimal v) { this.pendingRating = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public BigDecimal getDelta() { return delta; }
    public void setDelta(BigDecimal v) { this.delta = v; }
    public java.time.Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(java.time.Instant v) { this.deletedAt = v; }
}
