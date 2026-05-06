package com.onesley.oneclick.entity.tenant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Entité {@code public.tenants} — racine multi-tenant whitelabel (oneclick,
 * restopro, palmeraie, homu, etc.).
 *
 * <p>Pattern pilote : <i>JSONB libre + audit partiel ({@code created_by} seul, sans
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

    @Column(name = "company_settings_id")
    private UUID companySettingsId;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> features = new HashMap<>();

    protected Tenant() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getLegalName() { return legalName; }
    public String getStatus() { return status; }
    public UUID getCompanySettingsId() { return companySettingsId; }
    public UUID getCreatedBy() { return createdBy; }
    public Map<String, Object> getFeatures() { return features; }
}
