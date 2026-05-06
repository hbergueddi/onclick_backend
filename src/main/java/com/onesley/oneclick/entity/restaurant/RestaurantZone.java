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
 * Entité {@code public.restaurant_zones} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "restaurant_zones")
public class RestaurantZone extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "description")
    private String description;

    @NotNull
    @Column(name = "capacite", nullable = false)
    private Integer capacite;

    @NotNull
    @Column(name = "tables_count", nullable = false)
    private Integer tablesCount;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    protected RestaurantZone() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public Integer getCapacite() { return capacite; }
    public Integer getTablesCount() { return tablesCount; }
    public String getStatus() { return status; }
}
