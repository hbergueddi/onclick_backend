package com.onesley.oneclick.core.auth.internal;

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
 * Invitation d'activation de compte membre (Gap #10, V88).
 *
 * <p>Émise quand un staff inscrit un <b>nouveau</b> membre (compte créé serveur-side
 * avec mot de passe aléatoire). Le membre reçoit un lien magique contenant un token
 * <b>clair</b> (jamais persisté — seul son {@code tokenHash} SHA-256 l'est).
 * L'acceptation (publique, gardée par le token) définit le mot de passe + émet un JWT.</p>
 *
 * <p>{@code status} ∈ {@code pending|accepted|revoked|expired} (CHECK base). Single-use :
 * {@code pending → accepted}. Symétrique de {@code core.tenant.TenantAdminInvite} (E2),
 * mais ciblant un user existant (pas de création de compte à l'acceptation).</p>
 */
@Entity
@Table(name = "account_activation_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountActivationInvite {
    // Timestamps (created_at/updated_at) gérés par DEFAULT now() en base — pas de
    // TimestampedEntity ici pour rester aligné avec les autres entités core.auth
    // (qui n'introduisent pas de dépendance vers le module audit).

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, length = 256)
    private String email;

    /** SHA-256 (hex) du token clair envoyé par email. Le token clair n'est jamais stocké. */
    @Column(name = "token_hash", nullable = false, length = 128, updatable = false)
    private String tokenHash;

    @Column(name = "status", nullable = false, length = 16)
    @Setter private String status = "pending";

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    @Setter private Instant acceptedAt;

    public AccountActivationInvite(UUID id, UUID userId, String email, String tokenHash,
                                   UUID createdBy, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.createdBy = createdBy;
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
        return id != null && Objects.equals(id, ((AccountActivationInvite) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
