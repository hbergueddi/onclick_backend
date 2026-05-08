package com.onesley.oneclick.entity.auth;

import com.onesley.oneclick.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.custom_roles} — rôles custom configurables par tenant
 * (tenant_admins peuvent en créer pour leurs équipes).
 *
 * <p>Pattern : audit niveau 1 ({@code created_at} + {@code updated_at} +
 * {@code created_by} + {@code modified_by}) via {@link AuditedEntity}.
 *
 * <h3>Jointures JPA (passe 3 — décisions explicites)</h3>
 * <ul>
 *   <li>Aucune FK directe sur cette entité (pas de tenant_id explicite — relation
 *       établie via les permissions JSONB et l'audit {@code created_by}).</li>
 *   <li>{@code permissions jsonb} → {@code Map<String, Object>} libre. Les valeurs
 *       sont des slugs de permissions (mêmes que dans
 *       {@link StaffRolePermission#permissionId}).</li>
 * </ul>
 */
@Entity
@Table(name = "custom_roles")
public class CustomRole extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @NotNull
    @Column(name = "permissions", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> permissions = new HashMap<>();

    protected CustomRole() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getPermissions() { return permissions; }

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
        CustomRole that = (CustomRole) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
