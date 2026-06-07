package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantAnnouncementDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Annonce éphémère 24h d'un restaurant (Gap #6 — port legacy 14/05).
 *
 * <p>Affichée sur la fiche restaurant (spotlight membre) sous « Réserver une table ».
 * 1 seule active par restaurant (l'unicité est garantie par
 * {@code RestaurantAnnouncementService} à la création, pas par trigger SQL).
 *
 * <p>Pas de {@code TimestampedEntity} : la table n'a pas de colonne {@code updated_at}
 * (annonce immuable, créée puis expirée — jamais éditée). {@code createdAt}/{@code expiresAt}
 * sont posés par le service via {@link java.time.Clock} (testable).
 */
@Entity
@Table(name = "restaurant_announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantAnnouncement {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "message", nullable = false, length = 280)
    private String message;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public RestaurantAnnouncement(UUID id, UUID restaurantId, String message, UUID authorId,
                                  Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.message = message;
        this.authorId = authorId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public RestaurantAnnouncementDto toDto() {
        return new RestaurantAnnouncementDto(id, restaurantId, message, authorId, createdAt, expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        RestaurantAnnouncement that = (RestaurantAnnouncement) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
