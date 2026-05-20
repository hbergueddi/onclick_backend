package com.onesley.oneclick.modules.payment.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.TransactionDto;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Journal des événements provider (Stripe webhook, CMI callback, etc.). */
@Entity
@Table(name = "payment_transactions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "provider_response", columnDefinition = "jsonb") private Map<String, Object> providerResponse = new HashMap<>();
     @Column(name = "event_type", nullable = false, length = 64) private String eventType;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    public PaymentTransaction(UUID id, UUID paymentId, String eventType, Map<String, Object> providerResponse) {
        this.id = id; this.paymentId = paymentId; this.eventType = eventType;
        if (providerResponse != null) this.providerResponse = providerResponse;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public TransactionDto toDto() {
        return new TransactionDto(id, paymentId, eventType, providerResponse, createdAt);
    }

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
