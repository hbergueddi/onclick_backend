package com.onesley.oneclick.entity.restaurant;

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

import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_tables} — tables physiques d'un restaurant
 * (numéro, capacité, position, zone).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code zone_id NOT NULL} → {@link RestaurantZone} en {@code @ManyToOne(LAZY)}, optional=false.
 *       Note : si le DDL évolue vers {@code zone_id} nullable (réassignation), cf.
 *       cascade strategy de {@link RestaurantZone}.</li>
 * </ul>
 */
@Entity
@Table(name = "restaurant_tables")
public class RestaurantTable extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Jointure restaurant_id ─────────────────────────────────────────────
    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    // ─── Jointure zone_id ───────────────────────────────────────────────────
    @Column(name = "zone_id", nullable = false, insertable = false, updatable = false)
    private UUID zoneId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private RestaurantZone zone;

    @NotNull
    @Column(name = "numero", nullable = false)
    private Integer numero;

    @NotNull
    @Column(name = "capacite", nullable = false)
    private Integer capacite;

    //Doit etre enum
    @NotBlank
    @Column(name = "forme", nullable = false)
    private String forme;

    @Column(name = "position")
    private String position;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    protected RestaurantTable() {
        // JPA
    }

    public UUID getId() { return id; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getRestaurantId() { return restaurantId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getZoneId() { return zoneId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public RestaurantZone getZone() { return zone; }
    public void setZone(RestaurantZone zone) { this.zone = zone; }

    public Integer getNumero() { return numero; }
    public Integer getCapacite() { return capacite; }
    public String getForme() { return forme; }
    public String getPosition() { return position; }
    public String getStatus() { return status; }

    // ─── equals / hashCode anti-proxy LAZY ──────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        RestaurantTable that = (RestaurantTable) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
