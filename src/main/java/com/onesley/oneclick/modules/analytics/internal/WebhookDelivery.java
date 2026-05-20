package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.WebhookDeliveryDto;
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
import lombok.Setter;

/** Historique des appels webhook. */
@Entity
@Table(name = "webhook_deliveries")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookDelivery {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "webhook_id", nullable = false, insertable = false, updatable = false) private UUID webhookId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "webhook_id", nullable = false) private Webhook webhook;
     @Column(name = "event_type", nullable = false, length = 64) private String eventType;
     @JdbcTypeCode(SqlTypes.JSON) @Column(name = "payload", nullable = false, columnDefinition = "jsonb") private Map<String, Object> payload = new HashMap<>();
    @Column(name = "status_code") @Setter private Integer statusCode;
    @Column(name = "response_body", columnDefinition = "text") @Setter private String responseBody;
    @Column(name = "attempts", nullable = false) private Integer attempts = 0;
    @Column(name = "succeeded_at") private Instant succeededAt;
    @Column(name = "failed_at") private Instant failedAt;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    public WebhookDelivery(UUID id, Webhook webhook, String eventType, Map<String, Object> payload) {
        this.id = id; this.webhook = webhook; this.eventType = eventType;
        if (payload != null) this.payload = payload;
    }
    public void incrementAttempts() { this.attempts = (this.attempts == null ? 0 : this.attempts) + 1; }
    public void markSucceeded() { this.succeededAt = Instant.now(); }
    public void markFailed() { this.failedAt = Instant.now(); }

    /** Mapping vers le DTO public exposé hors du module. */
    public WebhookDeliveryDto toDto() {
        return new WebhookDeliveryDto(id, webhookId, eventType, payload, statusCode, attempts,
            succeededAt, failedAt, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((WebhookDelivery) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
