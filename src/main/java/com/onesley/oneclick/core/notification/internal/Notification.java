package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Notification unitaire — multi-canal (§7).
 *
 * <p>Pattern microservice : pas de FK JPA vers {@code User} (qui vit dans oneclick-core).
 * Seulement {@code recipient_user_id : UUID}. Cohérence référentielle assurée par la DB
 * (FK Postgres existe toujours) mais l'entité JPA reste isolée.
 */
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "type", nullable = false, length = 64)
     private String type;

    @Column(name = "channel", nullable = false, length = 64)
     private String channel = "inapp";

    @Column(name = "title", nullable = false, length = 128)
     private String title;

    @Column(name = "body", nullable = false, length = 1024)
     private String body;

    @Column(name = "link", length = 512)
    @Setter private String link;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "read_at")
    private Instant readAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public Notification(UUID id, UUID recipientUserId, String type, String channel, String title, String body) {
        this.id = id;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.channel = channel;
        this.title = title;
        this.body = body;
    }
    public boolean isRead() { return readAt != null; }
    public void markRead() { this.readAt = Instant.now(); }

    /** Mapping vers le DTO public exposé hors du module. */
    public NotificationDto toDto() {
        return new NotificationDto(id, recipientUserId, type, channel, title, body, link, metadata, readAt, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        Notification that = (Notification) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
