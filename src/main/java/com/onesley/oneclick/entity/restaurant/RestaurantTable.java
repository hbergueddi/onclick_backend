package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_tables} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "restaurant_tables")
public class RestaurantTable extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "numero", nullable = false)
    private Integer numero;

    @Column(name = "capacite", nullable = false)
    private Integer capacite;

    @Column(name = "forme", nullable = false)
    private String forme;

    @Column(name = "position")
    private String position;

    @Column(name = "status", nullable = false)
    private String status;

    protected RestaurantTable() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getZoneId() { return zoneId; }
    public Integer getNumero() { return numero; }
    public Integer getCapacite() { return capacite; }
    public String getForme() { return forme; }
    public String getPosition() { return position; }
    public String getStatus() { return status; }
}
