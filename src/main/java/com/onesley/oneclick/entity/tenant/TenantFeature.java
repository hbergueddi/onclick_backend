package com.onesley.oneclick.entity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.tenant_features} — feature flags par tenant.
 *
 * <p>Pattern : <i>PK composite mixte (tenant_id UUID, feature_key String)</i>.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code @MapsId("tenantId")} → @Id tenantId dérivé de tenant.id ({@link Tenant})</li>
 *   <li>{@code featureKey} reste {@code @Id String} pur — slug textuel
 *       (ex: {@code "snap2earn_enabled"}, {@code "elite_club_enabled"}), pas une FK.</li>
 * </ul>
 *
 * <p><b>Note de cohérence</b> : Tenant a aussi {@code features jsonb} (Map<String,Object>),
 * et cette table {@code tenant_features} fait la même chose en relationnel.
 * <b>Double source de vérité</b> à clarifier en V2 (Phase 11+) — pour l'instant
 * les deux coexistent.
 */
@Entity
@Table(name = "tenant_features")
@IdClass(TenantFeatureId.class)
public class TenantFeature {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "feature_key", nullable = false)
    private String featureKey;

    // ─── Jointure tenant (PK partielle via @MapsId) ─────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tenantId")
    @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private Tenant tenant;

    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantFeature() {
        // JPA
    }

    public TenantFeature(Tenant tenant, String featureKey, Boolean enabled, Instant updatedAt) {
        this.tenant = tenant;
        this.featureKey = featureKey;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    public UUID getTenantId() { return tenantId; }
    public String getFeatureKey() { return featureKey; }

    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Instant getUpdatedAt() { return updatedAt; }

    // ─── equals / hashCode anti-proxy LAZY ──────────────────────────────────

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
        TenantFeature that = (TenantFeature) o;
        return Objects.equals(tenantId, that.tenantId)
            && Objects.equals(featureKey, that.featureKey);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
