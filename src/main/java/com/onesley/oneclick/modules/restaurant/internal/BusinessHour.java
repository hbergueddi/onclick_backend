package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

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
public class BusinessHour extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Min(0)
    @Max(6)
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected BusinessHour() {
        // JPA
    }

    public BusinessHour(UUID id, String entityType, UUID entityId, Integer dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.entityType = entityType;
        this.entityId = entityId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() { return id; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }

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
