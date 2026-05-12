package com.onesley.oneclick.modules.loyalty.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Redemption — utilisation de points pour obtenir une réduction.
 *
 * <p>Audit dédié séparé de loyalty_transactions (qui contient le mouvement de
 * points correspondant). {@code otp_validated} = vrai si la redemption a dû
 * passer par une validation OTP (gros montants).
 */
@Entity
@Table(name = "redemptions")
@EntityListeners(AuditingEntityListener.class)
public class Redemption {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @NotNull
    @Min(1)
    @Column(name = "points_used", nullable = false)
    private Integer pointsUsed;

    @NotNull
    @DecimalMin("0.01")
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "otp_validated", nullable = false)
    private boolean otpValidated = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdById;

    protected Redemption() {
        // JPA
    }

    public Redemption(UUID id, UUID accountId, Integer pointsUsed, BigDecimal discountAmount) {
        this.id = id;
        this.accountId = accountId;
        this.pointsUsed = pointsUsed;
        this.discountAmount = discountAmount;
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public Integer getPointsUsed() { return pointsUsed; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public boolean isOtpValidated() { return otpValidated; }
    public void setOtpValidated(boolean otpValidated) { this.otpValidated = otpValidated; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedById() { return createdById; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        Redemption that = (Redemption) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
