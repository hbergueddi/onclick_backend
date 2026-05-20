package com.onesley.oneclick.modules.payment.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentDto;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Paiement applicatif (réservation, redemption, etc.). */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "payment_method_id") @Setter private UUID paymentMethodId;
      @Column(name = "amount", nullable = false, precision = 12, scale = 2) private BigDecimal amount;
     @Column(name = "currency", nullable = false, length = 64) @Setter private String currency = "MAD";
    
    @Column(name = "status", nullable = false, length = 64) @Setter private String status = "pending";
    @Column(name = "provider", length = 64) @Setter private String provider;
    @Column(name = "transaction_ref", length = 64) @Setter private String transactionRef;
    @Column(name = "reference_type", length = 64) @Setter private String referenceType;
    @Column(name = "reference_id") @Setter private UUID referenceId;
    @Column(name = "completed_at") private Instant completedAt;
    public Payment(UUID id, UUID userId, BigDecimal amount) { this.id = id; this.userId = userId; this.amount = amount; }
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
