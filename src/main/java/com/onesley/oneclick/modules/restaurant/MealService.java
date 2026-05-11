package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Créneau de service d'un restaurant (brunch, déjeuner, dîner) avec horaires.
 *
 * <p>Renommé de {@code RestaurantService} en {@code MealService} pour éviter
 * la collision avec le Spring {@code @Service RestaurantCatalogService}.
 * La table SQL reste {@code restaurant_services} (mapping conservé).
 */
@Entity
@Table(name = "restaurant_services")
public class MealService extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected MealService() {
        // JPA
    }

    public MealService(UUID id, Restaurant restaurant, String name, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.restaurant = restaurant;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public String getName() { return name; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((MealService) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
