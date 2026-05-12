package com.onesley.oneclick.core.notification.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
 * Notification unitaire — multi-canal (§7).
 *
 * <p>Pattern microservice : pas de FK JPA vers {@code User} (qui vit dans oneclick-core).
 * Seulement {@code recipient_user_id : UUID}. Cohérence référentielle assurée par la DB
 * (FK Postgres existe toujours) mais l'entité JPA reste isolée.
 */
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @NotBlank
    @Pattern(regexp = "^(reservation|loyalty|promotion|community|support|system|announcement)$")
    @Column(name = "type", nullable = false)
    private String type;

    @NotBlank
    @Pattern(regexp = "^(inapp|push|email|sms)$")
    @Column(name = "channel", nullable = false)
    private String channel = "inapp";

    @NotBlank
    @Column(name = "title", nullable = false)
    private String title;

    @NotBlank
    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "link")
    private String link;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "read_at")
    private Instant readAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected Notification() {
        // JPA
    }

    public Notification(UUID id, UUID recipientUserId, String type, String channel, String title, String body) {
        this.id = id;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.channel = channel;
        this.title = title;
        this.body = body;
    }

    public UUID getId() { return id; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public String getType() { return type; }
    public String getChannel() { return channel; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getReadAt() { return readAt; }
    public boolean isRead() { return readAt != null; }
    public void markRead() { this.readAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }

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
