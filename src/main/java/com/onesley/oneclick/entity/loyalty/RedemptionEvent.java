package com.onesley.oneclick.entity.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.redemption_events} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "redemption_events")
@EntityListeners(AuditingEntityListener.class)
public class RedemptionEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "scanned_by")
    private UUID scannedBy;

    @Column(name = "ticket_ref")
    private String ticketRef;

    @Column(name = "ticket_montant", nullable = false)
    private BigDecimal ticketMontant;

    @Column(name = "points_redeemed", nullable = false)
    private Integer pointsRedeemed;

    @Column(name = "discount_dh", nullable = false)
    private BigDecimal discountDh;

    @Column(name = "client_tier")
    private String clientTier;

    @Column(name = "effective_point_value_mad", nullable = false)
    private BigDecimal effectivePointValueMad;

    @Column(name = "accepted", nullable = false)
    private Boolean accepted;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "flag_ratio_high", nullable = false)
    private Boolean flagRatioHigh;

    @Column(name = "flag_daily_near_cap", nullable = false)
    private Boolean flagDailyNearCap;

    @Column(name = "flag_first_redemption", nullable = false)
    private Boolean flagFirstRedemption;

    @Column(name = "flag_large_absolute", nullable = false)
    private Boolean flagLargeAbsolute;

    protected RedemptionEvent() {
        // JPA
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getClientId() { return clientId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getScannedBy() { return scannedBy; }
    public String getTicketRef() { return ticketRef; }
    public BigDecimal getTicketMontant() { return ticketMontant; }
    public Integer getPointsRedeemed() { return pointsRedeemed; }
    public BigDecimal getDiscountDh() { return discountDh; }
    public String getClientTier() { return clientTier; }
    public BigDecimal getEffectivePointValueMad() { return effectivePointValueMad; }
    public Boolean getAccepted() { return accepted; }
    public String getRejectionReason() { return rejectionReason; }
    public Boolean getFlagRatioHigh() { return flagRatioHigh; }
    public Boolean getFlagDailyNearCap() { return flagDailyNearCap; }
    public Boolean getFlagFirstRedemption() { return flagFirstRedemption; }
    public Boolean getFlagLargeAbsolute() { return flagLargeAbsolute; }
}
