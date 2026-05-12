package com.onesley.oneclick.modules.event.api;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.tenant.internal.Tenant;
import com.onesley.oneclick.modules.restaurant.internal.Restaurant;
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
    @Column(name = "restaurant_id", insertable = false, updatable = false) private UUID restaurantId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "restaurant_id") private Restaurant restaurant;
    @NotBlank @Column(name = "title", nullable = false) private String title;
    @Column(name = "description") private String description;
    @Column(name = "event_type") private String eventType;
    @NotNull @Column(name = "event_at", nullable = false) private Instant eventAt;
    @Column(name = "capacity") private Integer capacity;

    protected Event() {}
    public Event(UUID id, Tenant tenant, String title, Instant eventAt) {
        this.id = id; this.tenant = tenant; this.title = title; this.eventAt = eventAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getEventAt() { return eventAt; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

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
