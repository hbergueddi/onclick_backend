package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceDto;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "resource_type", nullable = false, length = 64)
     private String resourceType;

    @Column(name = "name", nullable = false, length = 128)
    @Setter private String name;

    @Column(name = "description", length = 1024)
    @Setter private String description;

    @Column(name = "capacity")
    @Setter private Integer capacity;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = true;

    /** Horaires d'ouverture par jour de semaine ({@code mon..sun} → plages {@code "HH:MM-HH:MM"}) — JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_hours", columnDefinition = "jsonb")
    @Setter private Map<String, List<String>> openingHours;

    /** Durée d'un créneau en minutes (génération de la grille de créneaux). */
    @Column(name = "slot_duration_minutes")
    @Setter private Integer slotDurationMinutes;

    /** Nombre max d'invités en plus de l'organisateur. */
    @Column(name = "max_invitees")
    @Setter private Integer maxInvitees;

    public Resource(UUID id, Tenant tenant, String resourceType, String name) {
        this.id = id;
        this.tenant = tenant;
        this.resourceType = resourceType;
        this.name = name;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public ResourceDto toDto() {
        return new ResourceDto(id, tenantId, resourceType, name, description, capacity, enabled, getCreatedAt(),
            openingHours, slotDurationMinutes, maxInvitees);
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
