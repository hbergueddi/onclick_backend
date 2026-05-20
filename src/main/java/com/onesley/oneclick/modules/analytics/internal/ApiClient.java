package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.modules.analytics.api.AnalyticsDtos.ApiClientDto;
import jakarta.persistence.*;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Client API (intégration partenaire). */
@Entity
@Table(name = "api_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiClient extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "tenant_id", insertable = false, updatable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tenant_id") @Setter private Tenant tenant;
     @Column(name = "name", nullable = false, length = 128) @Setter private String name;
    @Column(name = "description", length = 1024) @Setter private String description;
    @Column(name = "enabled", nullable = false) @Setter private boolean enabled = true;
    public ApiClient(UUID id, String name) { this.id = id; this.name = name; }

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
