package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.tenant.Tenant;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/** Ressource bookable générique (padel, spa, golf, coiffeur, gym...). */
@Entity
@Table(name = "resources")
public class Resource extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotBlank
    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected Resource() {}

    public Resource(UUID id, Tenant tenant, String resourceType, String name) {
        this.id = id;
        this.tenant = tenant;
        this.resourceType = resourceType;
        this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public String getResourceType() { return resourceType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((Resource) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
