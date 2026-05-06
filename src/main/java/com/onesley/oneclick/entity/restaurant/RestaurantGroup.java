package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_groups} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "restaurant_groups")
public class RestaurantGroup extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    protected RestaurantGroup() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getLogoUrl() { return logoUrl; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public UUID getTenantId() { return tenantId; }
}
