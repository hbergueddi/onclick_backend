package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.notification.api.NotificationDtos.DeviceTokenDto;
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
 * Token FCM/APNs par device — pour push notifications (mobile + web).
 *
 * <p>Microservice pattern : pas de FK JPA vers User. Cohérence via FK Postgres.
 */
@Entity
@Table(name = "device_tokens")
public class DeviceToken extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotBlank
    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Pattern(regexp = "^(ios|android|web)$")
    @Column(name = "platform")
    private String platform;

    @Column(name = "app_id")
    private String appId;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected DeviceToken() {
        // JPA
    }

    public DeviceToken(UUID id, UUID userId, String token, String platform) {
        this.id = id;
        this.userId = userId;
        this.token = token;
        this.platform = platform;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getToken() { return token; }
    public String getPlatform() { return platform; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public Instant getLastUsedAt() { return lastUsedAt; }
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
