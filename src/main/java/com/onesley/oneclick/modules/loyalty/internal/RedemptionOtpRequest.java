package com.onesley.oneclick.modules.loyalty.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Demande d'OTP pour une grosse conversion de points (Gap #2).
 *
 * <p>Cycle de vie : {@code pending} → {@code consumed} (code validé) /
 * {@code expired} (5 min) / {@code cancelled} (remplacée ou trop de tentatives).
 * Le code en clair n'est JAMAIS stocké — seul son SHA-256 ({@code codeHash}).
 */
@Entity
@Table(name = "redemption_otp_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedemptionOtpRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

    @Column(name = "points_requested", nullable = false, updatable = false)
    private int pointsRequested;

    @Column(name = "ticket_montant", nullable = false, updatable = false)
    private BigDecimal ticketMontant;

    @Column(name = "estimated_discount_dh", nullable = false, updatable = false)
    private BigDecimal estimatedDiscountDh;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @Column(name = "status", nullable = false)
    @Setter private String status = "pending";

    @Column(name = "attempts", nullable = false)
    @Setter private int attempts = 0;

    @Column(name = "consumed_at")
    @Setter private Instant consumedAt;

    @Column(name = "consumed_for_ticket_ref")
    @Setter private String consumedForTicketRef;

    public RedemptionOtpRequest(
        UUID id, Instant createdAt, Instant expiresAt,
        UUID clientId, UUID restaurantId, UUID staffId,
        int pointsRequested, BigDecimal ticketMontant, BigDecimal estimatedDiscountDh,
        String codeHash
    ) {
        this.id = id;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.clientId = clientId;
        this.restaurantId = restaurantId;
        this.staffId = staffId;
        this.pointsRequested = pointsRequested;
        this.ticketMontant = ticketMontant;
        this.estimatedDiscountDh = estimatedDiscountDh;
        this.codeHash = codeHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        RedemptionOtpRequest that = (RedemptionOtpRequest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
