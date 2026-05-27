package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.loyalty.api.TierRestaurantOfferDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Offre de fidélité PAR niveau (Ruby/Sapphire/Émeraude) et PAR restaurant.
 * Introduit par la migration V48. Distinct des offres éditoriales (Offer) :
 * mappe un tier à un avantage (remise/cadeau/priorité…) pour un restaurant.
 */
@Entity
@Table(name = "tier_restaurant_offers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TierRestaurantOffer extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "tier_name", nullable = false)
    @Setter private String tierName;

    @Column(name = "offer_label", nullable = false)
    @Setter private String offerLabel;

    @Column(name = "offer_type", nullable = false)
    @Setter private String offerType = "remise";

    @Column(name = "offer_value")
    @Setter private String offerValue;

    @Column(name = "description")
    @Setter private String description;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = true;

    public TierRestaurantOffer(UUID id, UUID restaurantId, String tierName, String offerLabel) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.tierName = tierName;
        this.offerLabel = offerLabel;
    }

    public TierRestaurantOfferDto toDto() {
        return new TierRestaurantOfferDto(
            id, restaurantId, tierName, offerLabel, offerType, offerValue, description,
            enabled, getCreatedAt(), getUpdatedAt()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        TierRestaurantOffer that = (TierRestaurantOffer) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
