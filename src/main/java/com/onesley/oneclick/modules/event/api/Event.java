package com.onesley.oneclick.modules.event.api;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.tenant.api.Tenant;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Événement (soirée, dégustation, séminaire). */
@Entity
@Table(name = "events")
public class Event extends SoftDeletableAuditedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id", nullable = false) private Tenant tenant;
    @Column(name = "restaurant_id") private UUID restaurantId;
    @NotBlank @Column(name = "title", nullable = false) private String title;
    @Column(name = "description") private String description;
    @Column(name = "event_type") private String eventType;
    @NotNull @Column(name = "event_at", nullable = false) private Instant eventAt;
    @Column(name = "capacity") private Integer capacity;

    // ─── V20 — Sprint D Elite enrich ────────────────────────────────────
    @Column(name = "min_tier") private String minTier;
    @Column(name = "places_taken", nullable = false) private Integer placesTaken = 0;
    @Column(name = "image_url") private String imageUrl;
    @Column(name = "location_name") private String locationName;
    @Column(name = "is_active", nullable = false) private boolean isActive = true;
    @Column(name = "event_end") private Instant eventEnd;

    protected Event() {}
    public Event(UUID id, Tenant tenant, String title, Instant eventAt) {
        this.id = id; this.tenant = tenant; this.title = title; this.eventAt = eventAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public UUID getRestaurantId() { return restaurantId; }
    public void setRestaurantId(UUID restaurantId) { this.restaurantId = restaurantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getEventAt() { return eventAt; }
    public void setEventAt(Instant eventAt) { this.eventAt = eventAt; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    // V20 getters/setters
    public String getMinTier() { return minTier; }
    public void setMinTier(String minTier) { this.minTier = minTier; }
    public Integer getPlacesTaken() { return placesTaken; }
    public void setPlacesTaken(Integer placesTaken) { this.placesTaken = placesTaken; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { this.isActive = active; }
    public Instant getEventEnd() { return eventEnd; }
    public void setEventEnd(Instant eventEnd) { this.eventEnd = eventEnd; }

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
