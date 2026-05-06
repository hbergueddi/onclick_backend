package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Entité {@code public.tier_restaurant_offers} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "tier_restaurant_offers")
public class TierRestaurantOffer extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotBlank
    @Column(name = "tier_name", nullable = false)
    private String tierName;

    @NotBlank
    @Column(name = "offer_label", nullable = false)
    private String offerLabel;

    @NotBlank
    @Column(name = "offer_type", nullable = false)
    private String offerType;

    @NotBlank
    @Column(name = "offer_value", nullable = false)
    private String offerValue;

    @Column(name = "description")
    private String description;

    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    protected TierRestaurantOffer() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getTierName() { return tierName; }
    public String getOfferLabel() { return offerLabel; }
    public String getOfferType() { return offerType; }
    public String getOfferValue() { return offerValue; }
    public String getDescription() { return description; }
    public Boolean getEnabled() { return enabled; }
}
