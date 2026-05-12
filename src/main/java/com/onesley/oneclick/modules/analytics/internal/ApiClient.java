package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.ApiClientDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** Client API (intégration partenaire). */
@Entity
@Table(name = "api_clients")
public class ApiClient extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "tenant_id", insertable = false, updatable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tenant_id") private Tenant tenant;
    @NotBlank @Column(name = "name", nullable = false) private String name;
    @Column(name = "description") private String description;
    @Column(name = "enabled", nullable = false) private boolean enabled = true;

    protected ApiClient() {}
    public ApiClient(UUID id, String name) { this.id = id; this.name = name; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Mapping vers le DTO public exposé hors du module. */
    public ApiClientDto toDto() {
        return new ApiClientDto(id, tenantId, name, description, enabled, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((ApiClient) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
