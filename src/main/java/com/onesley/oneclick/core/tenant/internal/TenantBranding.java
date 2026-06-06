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

    @Column(name = "logo_url", length = 512)
    @Setter private String logoUrl;

    @Column(name = "primary_color", length = 64)
    @Setter private String primaryColor;

    @Column(name = "accent_color", length = 64)
    @Setter private String accentColor;

    @Column(name = "custom_domain", unique = true, length = 512)
    @Setter private String customDomain;

    // V74 — parité 1:1 éditeur branding (champs additionnels whitelabel).
    @Column(name = "background_color", length = 64)
    @Setter private String backgroundColor;

    @Column(name = "logo_dark_url", length = 512)
    @Setter private String logoDarkUrl;

    @Column(name = "favicon_url", length = 512)
    @Setter private String faviconUrl;

    @Column(name = "tagline", length = 256)
    @Setter private String tagline;

    /** Nom de l'app iOS Win (stocké, non consommé par les builds dans ce périmètre). */
    @Column(name = "app_name_win", length = 64)
    @Setter private String appNameWin;

    /** Nom de l'app iOS Store (stocké, non consommé par les builds dans ce périmètre). */
    @Column(name = "app_name_store", length = 64)
    @Setter private String appNameStore;

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
