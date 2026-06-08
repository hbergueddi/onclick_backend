package com.onesley.oneclick.core.membership.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
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
 * Appartenance ADDITIVE d'un client à un programme (tenant) — table {@code tenant_memberships} (V90).
 *
 * <p>Un client (compte OneClick unique, tenant <i>home</i> = oneclick) peut être membre de N
 * programmes (PCC, HOMU, futurs). {@code role_id} (nullable) porte le rôle programme utilisé en P1
 * pour plier les authorities ; {@code member_type} reprend l'ancien {@code users.pcc_member_type}.
 *
 * <p>Cycle de vie via {@code status} ({@code invited → active → revoked}) + soft-delete
 * ({@code deleted_at}). Une membership est <b>active</b> si {@code status = 'active'} ET non supprimée.
 */
@Entity
@Table(name = "tenant_memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantMembership extends TimestampedEntity {

    public static final String STATUS_INVITED = "invited";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_REVOKED = "revoked";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Le client membre. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Le programme (tenant) auquel le client est rattaché. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Type de membre (resident / non_resident / null) — repris de l'historique PCC. */
    @Column(name = "member_type", length = 32)
    @Setter private String memberType;

    /** Rôle programme accordant les permissions (P1) — nullable tant que non câblé. */
    @Column(name = "role_id")
    @Setter private UUID roleId;

    /** {@code invited} / {@code active} / {@code revoked} (CHECK en base). */
    @Column(name = "status", nullable = false, length = 32)
    @Setter private String status;

    /** Admin du tenant ayant invité (P2). */
    @Column(name = "invited_by", updatable = false)
    private UUID invitedBy;

    /** Date d'entrée effective dans le programme. */
    @Column(name = "joined_at")
    @Setter private Instant joinedAt;

    /** Soft-delete (révocation définitive). */
    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;

    public TenantMembership(UUID id, UUID userId, UUID tenantId, String memberType, UUID roleId,
                            String status, UUID invitedBy, Instant joinedAt) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.memberType = memberType;
        this.roleId = roleId;
        this.status = status;
        this.invitedBy = invitedBy;
        this.joinedAt = joinedAt;
    }

    /** Membership exploitable : active ET non supprimée. */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status) && deletedAt == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((TenantMembership) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
