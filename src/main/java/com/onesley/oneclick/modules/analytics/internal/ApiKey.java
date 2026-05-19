package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.ApiKeyDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Size;

/** Clé API hashée. */
@Entity
@Table(name = "api_keys")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "api_client_id", nullable = false, insertable = false, updatable = false) private UUID apiClientId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "api_client_id", nullable = false) private ApiClient apiClient;
    @NotBlank @Column(name = "key_hash", nullable = false, unique = true) @Size(max = 255) private String keyHash;
    @NotBlank @Column(name = "key_prefix", nullable = false) @Size(max = 255) private String keyPrefix;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "scopes", columnDefinition = "jsonb") private List<String> scopes = new ArrayList<>();
    @Column(name = "enabled", nullable = false) @Setter private boolean enabled = true;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "expires_at") @Setter private Instant expiresAt;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    public ApiKey(UUID id, ApiClient apiClient, String keyHash, String keyPrefix) {
        this.id = id; this.apiClient = apiClient; this.keyHash = keyHash; this.keyPrefix = keyPrefix;
    }
    public void markUsed() { this.lastUsedAt = Instant.now(); }
    public void revoke() { this.revokedAt = Instant.now(); }

    /** Mapping vers le DTO public exposé hors du module. */
    public ApiKeyDto toDto() {
        return new ApiKeyDto(id, apiClientId, keyPrefix, scopes, enabled, lastUsedAt, expiresAt, createdAt, revokedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((ApiKey) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
