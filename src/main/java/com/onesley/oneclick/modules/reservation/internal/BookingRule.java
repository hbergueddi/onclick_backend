package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.reservation.api.BookingRuleDtos.BookingRuleDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Règles de réservation par restaurant (couverts max, durée slot, fenêtre annulation).
 */
@Entity
@Table(name = "booking_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingRule extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "max_guest", nullable = false)
    @Setter private Integer maxGuest = 12;

    /** Durée d'un slot en minutes (90 par défaut). */
    
    @Column(name = "slot_duration", nullable = false)
    @Setter private Integer slotDuration = 90;

    /** Fenêtre d'annulation gratuite en heures avant reservation_at. */
    
    @Column(name = "cancellation_window_hours", nullable = false)
    @Setter private Integer cancellationWindowHours = 2;

    public BookingRule(UUID id, UUID restaurantId) {
        this.id = id;
        this.restaurantId = restaurantId;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public BookingRuleDto toDto() {
        return new BookingRuleDto(id, restaurantId, maxGuest, slotDuration, cancellationWindowHours, getCreatedAt());
    }

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
