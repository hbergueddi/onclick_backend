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
 * Entité {@code public.redemption_otp_requests} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "redemption_otp_requests")
@EntityListeners(AuditingEntityListener.class)
public class RedemptionOtpRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "points_requested", nullable = false)
    private Integer pointsRequested;

    @Column(name = "ticket_montant", nullable = false)
    private BigDecimal ticketMontant;

    @Column(name = "estimated_discount_dh", nullable = false)
    private BigDecimal estimatedDiscountDh;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "consumed_for_ticket_ref")
    private String consumedForTicketRef;

    protected RedemptionOtpRequest() {
        // JPA
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getClientId() { return clientId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getStaffId() { return staffId; }
    public Integer getPointsRequested() { return pointsRequested; }
    public BigDecimal getTicketMontant() { return ticketMontant; }
    public BigDecimal getEstimatedDiscountDh() { return estimatedDiscountDh; }
    public String getCodeHash() { return codeHash; }
    public String getStatus() { return status; }
    public Integer getAttempts() { return attempts; }
    public Instant getConsumedAt() { return consumedAt; }
    public String getConsumedForTicketRef() { return consumedForTicketRef; }
}
