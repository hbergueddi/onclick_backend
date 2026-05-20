package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.notification.api.NotificationDtos.DeviceTokenDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Token FCM/APNs par device — pour push notifications (mobile + web).
 *
 * <p>Microservice pattern : pas de FK JPA vers User. Cohérence via FK Postgres.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    @Column(name = "platform", length = 64)
     private String platform;

    @Column(name = "app_id", length = 64)
    @Setter private String appId;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public DeviceToken(UUID id, UUID userId, String token, String platform) {
        this.id = id;
        this.userId = userId;
        this.token = token;
        this.platform = platform;
    }
    public void markUsed() { this.lastUsedAt = Instant.now(); }

    /** Mapping vers le DTO public exposé hors du module. */
    public DeviceTokenDto toDto() {
        return new DeviceTokenDto(id, userId, token, platform, appId, lastUsedAt, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        DeviceToken that = (DeviceToken) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
