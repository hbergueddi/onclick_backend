package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import com.onesley.oneclick.core.tenant.api.Tenant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Feature flag par tenant. Pattern : un couple (tenant_id, feature_code) est UNIQUE.
 */
@Entity
@Table(
    name = "tenant_features",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "feature_code"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantFeature extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "feature_code", nullable = false, length = 64)
     private String featureCode;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = false;

    public TenantFeature(UUID id, Tenant tenant, String featureCode, boolean enabled) {
        this.id = id;
        this.tenant = tenant;
        this.featureCode = featureCode;
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        TenantFeature that = (TenantFeature) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
