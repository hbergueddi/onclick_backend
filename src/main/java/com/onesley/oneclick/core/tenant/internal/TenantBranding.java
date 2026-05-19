package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import com.onesley.oneclick.core.tenant.api.Tenant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Branding visuel d'un tenant — 1-1 avec Tenant via {@code @MapsId} (la PK est tenant_id).
 */
@Entity
@Table(name = "tenant_brandings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantBranding extends TimestampedEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "logo_url")
    @Setter private String logoUrl;

    @Column(name = "primary_color")
    @Setter private String primaryColor;

    @Column(name = "accent_color")
    @Setter private String accentColor;

    @Column(name = "custom_domain", unique = true)
    @Setter private String customDomain;

    public TenantBranding(Tenant tenant) {
        this.tenant = tenant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        TenantBranding that = (TenantBranding) o;
        return tenantId != null && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
