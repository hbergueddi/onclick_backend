package com.onesley.oneclick.entity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.tenant_branding} — branding par tenant (logo, couleurs,
 * tagline, app names whitelabel).
 *
 * <h3>Pattern : <i>Shared primary key 1-1 avec {@link Tenant}</i></h3>
 *
 * <p>La PK ({@code tenant_id}) est <b>la même valeur</b> que {@code Tenant.id}.
 * Vérifié en DB : {@code tenant_branding_pkey} = {@code btree(tenant_id)} (1-1 strict).
 *
 * <p>Le pattern senior pour mapper ce cas est {@code @MapsId} sans argument :
 * <ul>
 *   <li>Le {@code @Id UUID tenantId} reste accessible directement.</li>
 *   <li>Le {@code @ManyToOne}/{@code @OneToOne tenant} est annoté {@code @MapsId} —
 *       Hibernate sait que la PK est dérivée de la FK Tenant, donc <b>une seule
 *       colonne {@code tenant_id} en jeu</b>, pas de double mapping.</li>
 *   <li>Pas besoin de {@code insertable=false, updatable=false} sur le UUID
 *       (géré par {@code @MapsId}).</li>
 * </ul>
 *
 * <p>Sans {@code @MapsId} → Hibernate verrait deux mappings concurrents pour
 * {@code tenant_id} (un comme {@code @Id UUID}, un comme {@code @JoinColumn})
 * et planterait au démarrage avec <i>"Mixed @Id annotations"</i>.
 *
 * <p>Côté inverse : {@link Tenant#getBranding()} expose ce TenantBranding via
 * {@code @OneToOne(mappedBy="tenant", fetch=LAZY)}.
 */
@Entity
@Table(name = "tenant_branding")
public class TenantBranding {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // ─── Shared primary key : @MapsId dérive l'@Id depuis la FK Tenant ─────
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "logo_dark_url")
    private String logoDarkUrl;

    @Column(name = "favicon_url")
    private String faviconUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "accent_color")
    private String accentColor;

    @Column(name = "background_color")
    private String backgroundColor;

    @Column(name = "tagline")
    private String tagline;

    @Column(name = "app_name_win")
    private String appNameWin;

    @Column(name = "app_name_store")
    private String appNameStore;

    @Column(name = "custom_domain")
    private String customDomain;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantBranding() {
        // JPA
    }

    public UUID getTenantId() { return tenantId; }

    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public String getLogoUrl() { return logoUrl; }
    public String getLogoDarkUrl() { return logoDarkUrl; }
    public String getFaviconUrl() { return faviconUrl; }
    public String getPrimaryColor() { return primaryColor; }
    public String getAccentColor() { return accentColor; }
    public String getBackgroundColor() { return backgroundColor; }
    public String getTagline() { return tagline; }
    public String getAppNameWin() { return appNameWin; }
    public String getAppNameStore() { return appNameStore; }
    public String getCustomDomain() { return customDomain; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ─── equals / hashCode anti-proxy LAZY ──────────────────────────────────
    // PK = tenantId — utilisé pour l'égalité.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        TenantBranding that = (TenantBranding) o;
        return tenantId != null && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
