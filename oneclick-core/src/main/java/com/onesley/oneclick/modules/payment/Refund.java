package com.onesley.oneclick.modules.payment;

import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Remboursement partiel ou total d'un Payment. */
@Entity
@Table(name = "refunds")
@EntityListeners(AuditingEntityListener.class)
public class Refund {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "payment_id", nullable = false, insertable = false, updatable = false) private UUID paymentId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @NotNull @DecimalMin("0.01") @Column(name = "amount", nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "reason") private String reason;
    @Pattern(regexp = "^(pending|succeeded|failed)$") @Column(name = "status", nullable = false) private String status = "pending";
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    @Column(name = "processed_at") private Instant processedAt;
    @CreatedBy @Column(name = "created_by", updatable = false) private UUID createdById;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", insertable = false, updatable = false) private User createdBy;

    protected Refund() {}
    public Refund(UUID id, Payment payment, BigDecimal amount) { this.id = id; this.payment = payment; this.amount = amount; }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public Payment getPayment() { return payment; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void markProcessed() { this.processedAt = Instant.now(); }
    public UUID getCreatedById() { return createdById; }

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
