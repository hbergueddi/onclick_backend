package com.onesley.oneclick.entity.tenant;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.contract.CompanySetting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.tenants} — racine multi-tenant whitelabel (oneclick,
 * restopro, palmeraie, homu, etc.).
 *
 * <p>Pattern : <i>JSONB libre + audit partiel ({@code created_by} seul, sans
 * {@code modified_by})</i>.
 *
 * <p><b>Pourquoi pas {@code extends AuditedEntity}</b> : la table {@code tenants}
 * n'a que {@code created_by} (pas de {@code modified_by}), alors que
 * {@link com.onesley.oneclick.audit.AuditedEntity} mappe les 2. Hibernate
 * {@code ddl-auto=validate} planterait sur la colonne absente. Solution
 * pragmatique : étendre {@link TimestampedEntity} (created_at + updated_at) et
 * déclarer {@code createdBy} manuellement avec {@code @CreatedBy}.
 *
 * <p>Mapping notable :
 * <ul>
 *   <li>{@code slug text UNIQUE} → {@link String}, indexé</li>
 *   <li>{@code status text} avec CHECK (actif/suspendu/archivé) → {@link String}
 *       (validation côté service Phase 11 ; pas un enum DB)</li>
 *   <li>{@code features jsonb NOT NULL DEFAULT '{}'} → {@code Map<String,Object>}
 *       (structure libre par tenant — feature flags, config UI, etc.)</li>
 *   <li>{@code created_by} renseigné via {@link CreatedBy} +
 *       {@link com.onesley.oneclick.audit.SecurityContextAuditorAware}</li>
 * </ul>
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code company_settings_id} → {@link CompanySetting} en {@code @ManyToOne(LAZY)}.
 *       <b>Pas un {@code @OneToOne}</b> (vérification DB : aucun UNIQUE sur cette colonne).
 *       Plusieurs tenants peuvent partager le même CompanySetting (ou colonne rarement
 *       renseignée).</li>
 *   <li>{@code branding} : {@code @OneToOne(mappedBy="tenant", LAZY)} vers
 *       {@link TenantBranding}. Owning side = TenantBranding (PK = tenant_id, shared
 *       primary key via {@code @MapsId}).</li>
 *   <li>{@code created_by} : reste UUID brut (audit field, convention).</li>
 *   <li>Tenant.restaurants / Tenant.profiles / Tenant.admins : <b>pas de
 *       {@code @OneToMany} inverse</b> par défaut — volume non borné (1042 restos
 *       OneClick standard, 17k profils). Repositories paginés à la place.</li>
 * </ul>
 */
@Entity
@Table(name = "tenants")
public class Tenant extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "status", nullable = false)
    private String status;

    // ─── Jointure company_settings_id (référence simple, pas 1-1) ───────────
    @Column(name = "company_settings_id", insertable = false, updatable = false)
    private UUID companySettingsId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_settings_id")
    private CompanySetting companySettings;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> features = new HashMap<>();

    // ─── Inverse OneToOne vers TenantBranding (1 par tenant, shared PK) ─────
    // Owning side = TenantBranding (sa PK = tenant_id directement via @MapsId).
    @OneToOne(mappedBy = "tenant", fetch = FetchType.LAZY)
    private TenantBranding branding;

    protected Tenant() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getLegalName() { return legalName; }
    public String getStatus() { return status; }

    /** Raccourci read-only (issu de la colonne FK). Utiliser pour DTOs/projections. */
    public UUID getCompanySettingsId() { return companySettingsId; }

    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public CompanySetting getCompanySettings() { return companySettings; }
    public void setCompanySettings(CompanySetting companySettings) { this.companySettings = companySettings; }

    public UUID getCreatedBy() { return createdBy; }
    public Map<String, Object> getFeatures() { return features; }

    /** Lazy 1-1 inverse — peut être null si le tenant n'a pas de branding configuré. */
    public TenantBranding getBranding() { return branding; }

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
        Tenant that = (Tenant) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
