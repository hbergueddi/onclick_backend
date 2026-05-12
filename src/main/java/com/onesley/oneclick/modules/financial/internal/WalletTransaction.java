package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.WalletTxDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Mouvement du wallet restaurateur (crédit/débit/commission/payout/adjustment). */
@Entity
@Table(name = "wallet_transactions")
@EntityListeners(AuditingEntityListener.class)
public class WalletTransaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Pattern(regexp = "^(credit|debit|commission|payout|adjustment)$")
    @Column(name = "type", nullable = false)
    private String type;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "reason")
    private String reason;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type")
    private String referenceType;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdBy;

    protected WalletTransaction() {}

    public WalletTransaction(UUID id, UUID restaurantId, String type, BigDecimal amount, String reason) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getReason() { return reason; }
    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedById() { return createdById; }

    /** Mapping vers le DTO public exposé hors du module. */
    public WalletTxDto toDto() {
        return new WalletTxDto(id, restaurantId, type, amount, balanceAfter, reason,
            referenceId, referenceType, createdAt, createdById);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((WalletTransaction) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
