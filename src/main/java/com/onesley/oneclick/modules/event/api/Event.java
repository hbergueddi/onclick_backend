package com.onesley.oneclick.modules.event.api;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.tenant.api.Tenant;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Événement (soirée, dégustation, séminaire). */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends SoftDeletableAuditedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id", nullable = false) private Tenant tenant;
    @Column(name = "restaurant_id") @Setter private UUID restaurantId;
    @NotBlank @Column(name = "title", nullable = false) @Setter private String title;
    @Column(name = "description") @Setter private String description;
    @Column(name = "event_type") @Setter private String eventType;
    @NotNull @Column(name = "event_at", nullable = false) @Setter private Instant eventAt;
    @Column(name = "capacity") @Setter private Integer capacity;

    // ─── V20 — Sprint D Elite enrich ────────────────────────────────────
    @Column(name = "min_tier") @Setter private String minTier;
    @Column(name = "places_taken", nullable = false) @Setter private Integer placesTaken = 0;
    @Column(name = "image_url") @Setter private String imageUrl;
    @Column(name = "location_name") @Setter private String locationName;
    @Column(name = "is_active", nullable = false) @Setter private boolean isActive = true;
    @Column(name = "event_end") @Setter private Instant eventEnd;
    public Event(UUID id, Tenant tenant, String title, Instant eventAt) {
        this.id = id; this.tenant = tenant; this.title = title; this.eventAt = eventAt;
    }

    // V20 getters/setters

    /** Helpers métier Elite. */
    public boolean hasCapacity() {
        return capacity == null || (placesTaken == null ? 0 : placesTaken) < capacity;
    }
    public void incrementPlacesTaken() {
        this.placesTaken = (this.placesTaken == null ? 0 : this.placesTaken) + 1;
    }
    public void decrementPlacesTaken() {
        this.placesTaken = Math.max(0, (this.placesTaken == null ? 0 : this.placesTaken) - 1);
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public EventDtos.EventDto toDto() {
        return new EventDtos.EventDto(
            id, tenantId, restaurantId, title, description, eventType,
            eventAt, eventEnd, capacity, placesTaken, minTier, imageUrl,
            locationName, isActive, getCreatedAt()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((Event) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
