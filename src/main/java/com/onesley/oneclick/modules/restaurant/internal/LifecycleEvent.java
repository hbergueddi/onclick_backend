package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.audit.CreatedAtEntity;
import com.onesley.oneclick.modules.restaurant.api.LifecycleEventDto;
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
 * Événement de cycle de vie d'un restaurant (inscription / validation /
 * suspension / réactivation / rejet / modification) — journal append-only,
 * vue admin GALAXY. Introduit par la migration V45.
 *
 * <p>Immuable → étend {@link CreatedAtEntity} (created_at seul). Le nom du
 * restaurant n'est pas porté par l'entité : il est résolu intra-module par
 * {@code LifecycleEventService} via {@code RestaurantRepository}.
 */
@Entity
@Table(name = "lifecycle_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LifecycleEvent extends CreatedAtEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id")
    @Setter private UUID restaurantId;

    @Column(name = "event_type", nullable = false)
    @Setter private String eventType;

    @Column(name = "details")
    @Setter private String details;

    @Column(name = "actor")
    @Setter private String actor;

    public LifecycleEvent(UUID id, String eventType) {
        this.id = id;
        this.eventType = eventType;
    }

    /** Mapping vers le DTO public ; le nom du restaurant est fourni par le service. */
    public LifecycleEventDto toDto(String restaurantName) {
        return new LifecycleEventDto(
            id, restaurantId, restaurantName, eventType, details, actor, getCreatedAt()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        LifecycleEvent that = (LifecycleEvent) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
