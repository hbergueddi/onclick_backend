package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantTableDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Table physique d'un restaurant (T01, T02...) rattachée à une zone.
 * Renommé en {@code RestaurantTable} (vs SQL keyword {@code table}).
 */
@Entity
@Table(
    name = "restaurant_tables",
    uniqueConstraints = @UniqueConstraint(columnNames = {"zone_id", "table_number"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantTable extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "zone_id", nullable = false, insertable = false, updatable = false)
    private UUID zoneId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private RestaurantZone zone;

    @NotBlank
    @Column(name = "table_number", nullable = false)
    @Setter private String tableNumber;

    @Min(1)
    @Column(name = "seats", nullable = false)
    @Setter private Integer seats;

    public RestaurantTable(UUID id, RestaurantZone zone, String tableNumber, Integer seats) {
        this.id = id;
        this.zone = zone;
        this.tableNumber = tableNumber;
        this.seats = seats;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public RestaurantTableDto toDto() {
        return new RestaurantTableDto(id, zoneId, tableNumber, seats, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        RestaurantTable that = (RestaurantTable) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
