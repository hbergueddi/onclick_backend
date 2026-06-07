package com.onesley.oneclick.core.tenant.internal;

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
 * Invitation d'un administrateur de tenant par email (E2 — TenantWelcome, V78).
 *
 * <p>Le SUPERADMIN crée l'invitation ; l'invité reçoit un lien magique contenant un
 * token <b>clair</b> (jamais persisté — seul son {@code tokenHash} SHA-256 l'est).
 * L'acceptation (publique, gardée par le token) crée/active le compte + assigne
 * {@code tenant_admins} + émet un JWT.</p>
 *
 * <p>{@code status} ∈ {@code pending|accepted|revoked|expired} (CHECK base).
 * {@code tenantRole} ∈ {@code owner|admin|viewer} (rôle tenant-scope, cf. {@link TenantAdmin}).</p>
 */
@Entity
@Table(name = "tenant_admin_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantAdminInvite extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "email", nullable = false, length = 256)
    private String email;

    /** SHA-256 (hex) du token clair envoyé par email. Le token clair n'est jamais stocké. */
    @Column(name = "token_hash", nullable = false, length = 128, updatable = false)
    private String tokenHash;

    @Column(name = "tenant_role", nullable = false, length = 16)
    private String tenantRole;

    @Column(name = "status", nullable = false, length = 16)
    @Setter private String status = "pending";

    @Column(name = "invited_by", updatable = false)
    private UUID invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    @Setter private Instant acceptedAt;

    @Column(name = "accepted_user_id")
    @Setter private UUID acceptedUserId;

    public TenantAdminInvite(UUID id, UUID tenantId, String email, String tokenHash,
                             String tenantRole, UUID invitedBy, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.tenantRole = tenantRole;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
    }

    /** Invitation exploitable : en attente et non expirée. */
    public boolean isRedeemable(Instant now) {
        return "pending".equals(status) && expiresAt.isAfter(now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((TenantAdminInvite) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
