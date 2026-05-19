package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Size;

/** Ressource bookable générique (padel, spa, golf, coiffeur, gym...). */
@Entity
@Table(name = "resources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    @Size(max = 255) private String resourceType;

    @NotBlank
    @Column(name = "name", nullable = false)
    @Setter @Size(max = 255) private String name;

    @Column(name = "description")
    @Setter @Size(max = 2000) private String description;

    @Column(name = "capacity")
    @Setter private Integer capacity;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = true;

    public Resource(UUID id, Tenant tenant, String resourceType, String name) {
        this.id = id;
        this.tenant = tenant;
        this.resourceType = resourceType;
        this.name = name;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public ResourceDto toDto() {
        return new ResourceDto(id, tenantId, resourceType, name, description, capacity, enabled, getCreatedAt());
    }

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
