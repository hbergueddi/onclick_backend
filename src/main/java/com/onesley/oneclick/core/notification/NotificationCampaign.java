package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Campagne marketing programmée — envoi batch à un segment de users.
 *
 * <p>Microservice pattern : pas de FK JPA vers Tenant/User (qui vivent dans oneclick-core).
 * Seulement les UUID. Cohérence référentielle assurée par la DB (FK Postgres existent).
 */
@Entity
@Table(name = "notification_campaigns")
public class NotificationCampaign extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Column(name = "title", nullable = false)
    private String title;

    @NotBlank
    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "target_segment")
    private String targetSegment;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Pattern(regexp = "^(draft|scheduled|sending|sent|cancelled|failed)$")
    @Column(name = "status", nullable = false)
    private String status = "draft";

    @Column(name = "created_by")
    private UUID createdById;

    protected NotificationCampaign() {
        // JPA
    }

    public NotificationCampaign(UUID id, UUID tenantId, String title, String message) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
        this.message = message;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTargetSegment() { return targetSegment; }
    public void setTargetSegment(String targetSegment) { this.targetSegment = targetSegment; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getSentAt() { return sentAt; }
    public void markSent() { this.sentAt = Instant.now(); this.status = "sent"; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getCreatedById() { return createdById; }
    public void setCreatedById(UUID createdById) { this.createdById = createdById; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        NotificationCampaign that = (NotificationCampaign) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
