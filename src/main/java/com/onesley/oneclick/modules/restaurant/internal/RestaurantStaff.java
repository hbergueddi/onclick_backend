package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Junction user × restaurant avec role_code applicatif (owner, manager, server, host, etc.).
 *
 * <p>Soft delete via {@code deleted_at} pour conserver l'historique des collaborations.
 */
@Entity
@Table(
    name = "restaurant_staffs",
    uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "user_id"})
)
public class RestaurantStaff extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected RestaurantStaff() {
        // JPA
    }

    public RestaurantStaff(UUID id, Restaurant restaurant, User user, String roleCode) {
        this.id = id;
        this.restaurant = restaurant;
        this.user = user;
        this.roleCode = roleCode;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public Instant getDeletedAt() { return deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }
    public void markDeleted() { this.deletedAt = Instant.now(); }

    /** Mapping vers le DTO public exposé hors du module. */
    public RestaurantStaffDto toDto() {
        return new RestaurantStaffDto(id, restaurantId, userId, roleCode, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        RestaurantStaff that = (RestaurantStaff) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
