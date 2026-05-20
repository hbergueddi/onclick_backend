package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.PricingDto;
import jakarta.persistence.*;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Grille tarifaire par ressource (1h padel, 30 min coiffeur, ...). */
@Entity
@Table(name = "resource_pricings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourcePricing extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "resource_id", nullable = false, insertable = false, updatable = false)
    private UUID resourceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Column(name = "name", nullable = false, length = 128)
     private String name;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    @Setter private BigDecimal price;

    @Column(name = "duration_minutes")
    @Setter private Integer durationMinutes;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = true;

    public ResourcePricing(UUID id, Resource resource, String name, BigDecimal price) {
        this.id = id;
        this.resource = resource;
        this.name = name;
        this.price = price;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public PricingDto toDto() {
        return new PricingDto(id, resourceId, name, price, durationMinutes, enabled, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((ResourcePricing) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
