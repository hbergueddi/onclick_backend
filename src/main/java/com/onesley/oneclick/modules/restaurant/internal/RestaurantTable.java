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
    @Setter private RestaurantZone zone;

    @Column(name = "table_number", nullable = false, length = 64)
    @Setter private String tableNumber;

    @Column(name = "seats", nullable = false)
    @Setter private Integer seats;

    /** Forme de la table (carree, rectangle, ronde…) — V50. */
    @Column(name = "shape", nullable = false, length = 64)
    @Setter private String shape = "carree";

    /** Position libre sur le plan de salle (texte/coordonnées) — V50. */
    @Column(name = "position", length = 128)
    @Setter private String position;

    /** Statut opérationnel (disponible, occupée, hors_service…) — V50. */
    @Column(name = "status", nullable = false, length = 64)
    @Setter private String status = "disponible";

    public RestaurantTable(UUID id, RestaurantZone zone, String tableNumber, Integer seats) {
        this.id = id;
        this.zone = zone;
        this.tableNumber = tableNumber;
        this.seats = seats;
    }

    /** Mapping vers le DTO public exposé hors du module. {@code zone.getId()} reflète
     * un éventuel changement de zone (le champ mirroir {@code zoneId} est read-only). */
    public RestaurantTableDto toDto() {
        return new RestaurantTableDto(id, zone.getId(), tableNumber, seats, shape, position, status, getCreatedAt());
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
