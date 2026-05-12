package com.onesley.oneclick.modules.payment.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Paiement applicatif (réservation, redemption, etc.). */
@Entity
@Table(name = "payments")
public class Payment extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "payment_method_id") private UUID paymentMethodId;
    @NotNull @DecimalMin("0.01") @Column(name = "amount", nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @NotBlank @Column(name = "currency", nullable = false) private String currency = "MAD";
    @Pattern(regexp = "^(pending|processing|succeeded|failed|cancelled|refunded)$")
    @Column(name = "status", nullable = false) private String status = "pending";
    @Column(name = "provider") private String provider;
    @Column(name = "transaction_ref") private String transactionRef;
    @Column(name = "reference_type") private String referenceType;
    @Column(name = "reference_id") private UUID referenceId;
    @Column(name = "completed_at") private Instant completedAt;

    protected Payment() {}
    public Payment(UUID id, UUID userId, BigDecimal amount) { this.id = id; this.userId = userId; this.amount = amount; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(UUID paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }
    public Instant getCompletedAt() { return completedAt; }
    public void markCompleted() { this.completedAt = Instant.now(); this.status = "succeeded"; }

    /** Mapping vers le DTO public exposé hors du module. */
    public PaymentDto toDto() {
        return new PaymentDto(id, userId, paymentMethodId, amount, currency, status, provider, transactionRef,
            referenceType, referenceId, completedAt, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((Payment) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
