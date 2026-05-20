package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Horaires polymorphiques — utilisable pour restaurant ET resource (§4).
 *
 * <p>Remplace le {@code opening_hours} jsonb legacy qui mélangeait 2 formats
 * (objet {jour: heures} vs array [{day, open, close}]) et causait des crashs
 * Hibernate quand l'un ou l'autre format était présent.
 *
 * <p>{@code day_of_week} : 0 = dimanche, 1 = lundi, ..., 6 = samedi (convention ISO).
 */
@Entity
@Table(name = "business_hours")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessHour extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 64)
     private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public BusinessHour(UUID id, String entityType, UUID entityId, Integer dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.entityType = entityType;
        this.entityId = entityId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        BusinessHour that = (BusinessHour) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
