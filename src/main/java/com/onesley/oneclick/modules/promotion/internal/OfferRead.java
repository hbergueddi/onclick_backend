package com.onesley.oneclick.modules.promotion.internal;

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
import lombok.Setter;

/**
 * État « lu » d'une offre par un utilisateur — table {@code offer_reads} (V63).
 *
 * <p>1 ligne par couple {@code (user_id, offer_id)} (contrainte UNIQUE) : le
 * marquage est un upsert idempotent (re-lire une offre déjà lue met à jour
 * {@code read_at}, sans créer de doublon). {@code created_at} reste figé sur le
 * 1er marquage.</p>
 *
 * <p>Refs par {@code UUID} plats (pas de {@code @ManyToOne} vers {@code User} /
 * {@code Offer}) : le module {@code modules.promotion} est CLOSED — même
 * convention que {@link OfferImpression} (V21), {@code loyalty} et
 * {@code reservation_guests}. Entité simple (pas de base auditée) — on porte
 * {@code created_at} manuellement comme {@code OfferImpression}.</p>
 */
@Entity
@Table(name = "offer_reads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferRead {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "offer_id", nullable = false, updatable = false)
    private UUID offerId;

    @Column(name = "read_at", nullable = false)
    @Setter private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OfferRead(UUID id, UUID userId, UUID offerId, Instant readAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.offerId = offerId;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((OfferRead) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
