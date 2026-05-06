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
 * Entité {@code public.restaurant_media} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "restaurant_media")
public class RestaurantMedia extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotBlank
    @Column(name = "type", nullable = false)
    private String type;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "url")
    private String url;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    protected RestaurantMedia() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getType() { return type; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getStatus() { return status; }
    public UUID getUploadedBy() { return uploadedBy; }
}
