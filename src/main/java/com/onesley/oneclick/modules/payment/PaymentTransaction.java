package com.onesley.oneclick.modules.payment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Journal des événements provider (Stripe webhook, CMI callback, etc.). */
@Entity
@Table(name = "payment_transactions")
@EntityListeners(AuditingEntityListener.class)
public class PaymentTransaction {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "provider_response", columnDefinition = "jsonb") private Map<String, Object> providerResponse = new HashMap<>();
    @NotBlank @Column(name = "event_type", nullable = false) private String eventType;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;

    protected PaymentTransaction() {}
    public PaymentTransaction(UUID id, UUID paymentId, String eventType, Map<String, Object> providerResponse) {
        this.id = id; this.paymentId = paymentId; this.eventType = eventType;
        if (providerResponse != null) this.providerResponse = providerResponse;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public Map<String, Object> getProviderResponse() { return providerResponse; }
    public String getEventType() { return eventType; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((PaymentTransaction) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
