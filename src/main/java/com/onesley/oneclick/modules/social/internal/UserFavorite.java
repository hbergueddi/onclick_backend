package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.social.api.SocialDtos.UserFavoriteDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Favoris d'un user — bouton ❤️ Pocket (Compass/Spotlight).
 *
 * <p>Table {@code user_favorites} : {@code created_at} uniquement (pas
 * d'{@code updated_at}/{@code deleted_at}), donc on n'hérite pas de
 * {@link com.onesley.oneclick.audit.TimestampedEntity}. UNIQUE {@code (user_id, restaurant_id)}.
 *
 * <p>{@code restaurantId} est référencé par UUID seul (pas d'{@code @ManyToOne})
 * car {@code Restaurant} est dans un autre module {@code modules/restaurant}
 * (Modulith CLOSED — pas de cross-package leak).
 */
@Entity
@Table(
    name = "user_favorites",
    uniqueConstraints = @UniqueConstraint(name = "user_favorites_unique", columnNames = {"user_id", "restaurant_id"})
)
@EntityListeners(AuditingEntityListener.class)
public class UserFavorite {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false) private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull @Column(name = "restaurant_id", nullable = false) private UUID restaurantId;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected UserFavorite() {}

    public UserFavorite(UUID id, User user, UUID restaurantId) {
        this.id = id;
        this.user = user;
        this.restaurantId = restaurantId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public UUID getRestaurantId() { return restaurantId; }
    public Instant getCreatedAt() { return createdAt; }

    /** Mapping vers le DTO public exposé hors du module. */
    public UserFavoriteDto toDto() {
        return new UserFavoriteDto(id, userId, restaurantId, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((UserFavorite) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
