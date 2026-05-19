package com.onesley.oneclick.modules.payment.internal;

import com.onesley.oneclick.modules.payment.api.PaymentDtos.RefundDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Remboursement partiel ou total d'un Payment. */
@Entity
@Table(name = "refunds")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @NotNull @DecimalMin("0.01") @Column(name = "amount", nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "reason") @Setter private String reason;
    @Pattern(regexp = "^(pending|succeeded|failed)$") @Column(name = "status", nullable = false) @Setter private String status = "pending";
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    @Column(name = "processed_at") private Instant processedAt;
    @CreatedBy @Column(name = "created_by", updatable = false) private UUID createdById;
    public Refund(UUID id, UUID paymentId, BigDecimal amount) { this.id = id; this.paymentId = paymentId; this.amount = amount; }
    public void markProcessed() { this.processedAt = Instant.now(); }

    /** Mapping vers le DTO public exposé hors du module. */
    public RefundDto toDto() {
        return new RefundDto(id, paymentId, amount, reason, status, createdAt, processedAt, createdById);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((Refund) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
