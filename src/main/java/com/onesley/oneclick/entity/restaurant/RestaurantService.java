package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.restaurant_services} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "restaurant_services")
public class RestaurantService extends TimestampedEntity {

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

    @NotBlank
    @Column(name = "heure_debut", nullable = false)
    private String heureDebut;

    @NotBlank
    @Column(name = "heure_fin", nullable = false)
    private String heureFin;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @NotNull
    @Column(name = "jours_actifs", nullable = false, columnDefinition = "text[]")
    private List<String> joursActifs = new ArrayList<>();

    @NotNull
    @Column(name = "capacite_max", nullable = false)
    private Integer capaciteMax;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @NotNull
    @Column(name = "clickgo_quota", nullable = false)
    private Integer clickgoQuota;

    protected RestaurantService() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getHeureDebut() { return heureDebut; }
    public String getHeureFin() { return heureFin; }
    public List<String> getJoursActifs() { return joursActifs; }
    public Integer getCapaciteMax() { return capaciteMax; }
    public String getStatus() { return status; }
    public Integer getClickgoQuota() { return clickgoQuota; }
}
