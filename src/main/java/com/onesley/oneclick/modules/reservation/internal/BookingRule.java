package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.restaurant.internal.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Règles de réservation par restaurant (couverts max, durée slot, fenêtre annulation).
 */
@Entity
@Table(name = "booking_rules")
public class BookingRule extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Min(1)
    @Column(name = "max_guest", nullable = false)
    private Integer maxGuest = 12;

    /** Durée d'un slot en minutes (90 par défaut). */
    @Min(15)
    @Column(name = "slot_duration", nullable = false)
    private Integer slotDuration = 90;

    /** Fenêtre d'annulation gratuite en heures avant reservation_at. */
    @Min(0)
    @Column(name = "cancellation_window_hours", nullable = false)
    private Integer cancellationWindowHours = 2;

    protected BookingRule() {
        // JPA
    }

    public BookingRule(UUID id, Restaurant restaurant) {
        this.id = id;
        this.restaurant = restaurant;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public Integer getMaxGuest() { return maxGuest; }
    public void setMaxGuest(Integer maxGuest) { this.maxGuest = maxGuest; }
    public Integer getSlotDuration() { return slotDuration; }
    public void setSlotDuration(Integer slotDuration) { this.slotDuration = slotDuration; }
    public Integer getCancellationWindowHours() { return cancellationWindowHours; }
    public void setCancellationWindowHours(Integer cancellationWindowHours) { this.cancellationWindowHours = cancellationWindowHours; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        BookingRule that = (BookingRule) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
