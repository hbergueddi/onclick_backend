package com.onesley.oneclick.core.audit_log.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event persisté (replay, audit, async processing).
 *
 * <p>Pattern : alternative pré-Spring Modulith event store. Quand on
 * activera {@code spring-modulith-starter-jpa} (avec table {@code event_publication}),
 * cette table pourra rester en parallèle pour les besoins métier (vs technique).
 */
@Entity
@Table(name = "system_events")
@EntityListeners(AuditingEntityListener.class)
public class SystemEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "type", nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected SystemEvent() {
        // JPA
    }

    public SystemEvent(UUID id, String type, Map<String, Object> payload) {
        this.id = id;
        this.type = type;
        if (payload != null) this.payload = payload;
    }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getProcessedAt() { return processedAt; }
    public boolean isProcessed() { return processedAt != null; }
    public void markProcessed() { this.processedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        SystemEvent that = (SystemEvent) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
