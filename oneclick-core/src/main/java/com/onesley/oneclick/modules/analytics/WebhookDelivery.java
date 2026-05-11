package com.onesley.oneclick.modules.analytics;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Historique des appels webhook. */
@Entity
@Table(name = "webhook_deliveries")
@EntityListeners(AuditingEntityListener.class)
public class WebhookDelivery {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "webhook_id", nullable = false, insertable = false, updatable = false) private UUID webhookId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "webhook_id", nullable = false) private Webhook webhook;
    @NotBlank @Column(name = "event_type", nullable = false) private String eventType;
    @NotNull @JdbcTypeCode(SqlTypes.JSON) @Column(name = "payload", nullable = false, columnDefinition = "jsonb") private Map<String, Object> payload = new HashMap<>();
    @Column(name = "status_code") private Integer statusCode;
    @Column(name = "response_body", columnDefinition = "text") private String responseBody;
    @Column(name = "attempts", nullable = false) private Integer attempts = 0;
    @Column(name = "succeeded_at") private Instant succeededAt;
    @Column(name = "failed_at") private Instant failedAt;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;

    protected WebhookDelivery() {}
    public WebhookDelivery(UUID id, Webhook webhook, String eventType, Map<String, Object> payload) {
        this.id = id; this.webhook = webhook; this.eventType = eventType;
        if (payload != null) this.payload = payload;
    }

    public UUID getId() { return id; }
    public UUID getWebhookId() { return webhookId; }
    public Webhook getWebhook() { return webhook; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public Integer getAttempts() { return attempts; }
    public void incrementAttempts() { this.attempts = (this.attempts == null ? 0 : this.attempts) + 1; }
    public Instant getSucceededAt() { return succeededAt; }
    public void markSucceeded() { this.succeededAt = Instant.now(); }
    public Instant getFailedAt() { return failedAt; }
    public void markFailed() { this.failedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }

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
