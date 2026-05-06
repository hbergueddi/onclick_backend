package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotNull
    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @NotNull
    @Column(name = "numero", nullable = false)
    private Integer numero;

    @NotNull
    @Column(name = "capacite", nullable = false)
    private Integer capacite;

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
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getZoneId() { return zoneId; }
    public Integer getNumero() { return numero; }
    public Integer getCapacite() { return capacite; }
    public String getForme() { return forme; }
    public String getPosition() { return position; }
    public String getStatus() { return status; }
}
