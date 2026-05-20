package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.promotion.api.OfferDto;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Offre / promotion par restaurant. */
@Entity
@Table(name = "offers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Offer extends SoftDeletableAuditedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "restaurant_id", nullable = false) private UUID restaurantId;
     @Column(name = "title", nullable = false, length = 128) @Setter private String title;
    @Column(name = "description", length = 1024) @Setter private String description;
     @Column(name = "starts_at", nullable = false) @Setter private Instant startsAt;
     @Column(name = "expires_at", nullable = false) @Setter private Instant expiresAt;
      @Column(name = "discount_pct", precision = 5, scale = 2) @Setter private BigDecimal discountPct;
    @Column(name = "discount_amount", precision = 12, scale = 2) @Setter private BigDecimal discountAmount;
    @Column(name = "enabled", nullable = false) @Setter private boolean enabled = true;

    /** Catégorie d'offre — promo | bonus | reco. */
    
    @Column(name = "type", nullable = false, length = 64) @Setter private String type = "promo";

    /** Bonus points fidélité — renseigné uniquement quand type='bonus'. */
    
    @Column(name = "pts") @Setter private Integer pts;

    // ─── V24 — Sprint K : champs exploités par l'admin (PromotionsLounge / OfferJet) ──

    /** Déclenche une push notification de masse à la publication. */
    @Column(name = "push_notify", nullable = false) @Setter private boolean pushNotify = false;

    /** URL de la bannière promo. */
    @Column(name = "image", length = 512) @Setter private String image;

    /** Segments clients ciblés (tous, fideles, nouveaux, inactifs, ruby, …). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "segments", columnDefinition = "text[]") @Setter private String[] segments;
    public Offer(UUID id, UUID restaurantId, String title, Instant startsAt, Instant expiresAt) {
        this.id = id; this.restaurantId = restaurantId; this.title = title; this.startsAt = startsAt; this.expiresAt = expiresAt;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public OfferDto toDto() {
        return new OfferDto(id, restaurantId, title, description, startsAt, expiresAt,
            discountPct, discountAmount, enabled, type, pts,
            pushNotify, image, segments == null ? List.of() : List.of(segments),
            getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((Offer) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
