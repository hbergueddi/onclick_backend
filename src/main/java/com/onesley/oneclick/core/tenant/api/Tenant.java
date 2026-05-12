package com.onesley.oneclick.core.tenant.api;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.tenant.api.TenantDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Racine multi-tenant. 1 ligne par marque whitelabel.
 */
@Entity
@Table(name = "tenants")
public class Tenant extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Pattern(regexp = "^[a-z0-9_-]+$", message = "slug doit être lowercase alphanumeric (a-z 0-9 _ -)")
    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @NotBlank
    @Pattern(regexp = "^(active|paused|archived)$")
    @Column(name = "status", nullable = false)
    private String status = "active";

    protected Tenant() {
        // JPA
    }

    public Tenant(UUID id, String name, String slug) {
        this.id = id;
        this.name = name;
        this.slug = slug;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /** Mapping vers le DTO public exposé hors du module. */
    public TenantDto toDto() {
        return new TenantDto(id, name, slug, status, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        Tenant that = (Tenant) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
