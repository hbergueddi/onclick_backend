package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.modules.promotion.api.OfferImpressionDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Impression (vue) d'une offre par un client — table {@code offer_impressions} (V21).
 * Log append-only : 1 ligne par événement de vue. {@code user_id} nullable (vue
 * anonyme possible). Pas d'{@code updated_at} → entité simple (pas TimestampedEntity).
 */
@Entity
@Table(name = "offer_impressions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferImpression {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "impression_type", nullable = false, length = 32)
    private String impressionType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public OfferImpression(UUID id, UUID offerId, UUID userId, String impressionType, Instant createdAt) {
        this.id = id;
        this.offerId = offerId;
        this.userId = userId;
        this.impressionType = impressionType;
        this.createdAt = createdAt;
    }

    public OfferImpressionDto toDto() {
        return new OfferImpressionDto(offerId, userId, impressionType, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((OfferImpression) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
