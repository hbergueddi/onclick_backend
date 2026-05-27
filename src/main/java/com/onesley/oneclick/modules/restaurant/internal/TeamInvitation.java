package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.audit.CreatedAtEntity;
import com.onesley.oneclick.modules.restaurant.api.TeamInvitationDto;
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
import lombok.Setter;

/**
 * Invitation d'équipe (pending staff) — gérée par un gérant de restaurant.
 * Introduit par la migration V47. Statut EN : pending | accepted | disabled.
 *
 * <p>Pas de ressource RBAC dédiée : géré sous l'autorité STAFF + ABAC
 * (RestaurantAccessGuard). Timestamp seul → {@link CreatedAtEntity}.
 */
@Entity
@Table(name = "team_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamInvitation extends CreatedAtEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "invited_by")
    @Setter private UUID invitedBy;

    @Column(name = "first_name", nullable = false)
    @Setter private String firstName;

    @Column(name = "last_name")
    @Setter private String lastName;

    @Column(name = "phone")
    @Setter private String phone;

    @Column(name = "role", nullable = false)
    @Setter private String role = "serveur";

    @Column(name = "status", nullable = false)
    @Setter private String status = "pending";

    @Column(name = "expires_at")
    @Setter private Instant expiresAt;

    public TeamInvitation(UUID id, UUID restaurantId, String firstName) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.firstName = firstName;
    }

    public TeamInvitationDto toDto() {
        return new TeamInvitationDto(
            id, restaurantId, invitedBy, firstName, lastName, phone, role, status, expiresAt, getCreatedAt()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        TeamInvitation that = (TeamInvitation) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
