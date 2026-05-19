package com.onesley.oneclick.core.identity.api;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.identity.api.UserDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Identité applicative — 1 user = 1 rôle (RBAC simplifié).
 *
 * <p>Étend {@link SoftDeletableAuditedEntity} : soft delete obligatoire via {@code deleted_at}.
 *
 * <p>Flags Spring Security natifs ({@code account_non_*}, {@code credentials_*}, {@code enabled})
 * → permettent d'implémenter directement {@link org.springframework.security.core.userdetails.UserDetails}
 * sans wrapper artificiel.
 *
 * <p>Le {@code tenantId} est nullable pour permettre des users globaux (admins plateforme).
 * Pour les users métier (clients, restaurateurs), {@code tenantId} est requis et filtre
 * la sécurité multi-tenant.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Tenant (nullable pour admins globaux) ───────────────────────────────
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    @Setter private com.onesley.oneclick.core.tenant.api.Tenant tenant;

    // ─── Rôle (1 seul, RBAC simplifié) ───────────────────────────────────────
    @Column(name = "role_id", nullable = false, insertable = false, updatable = false)
    private UUID roleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    @Setter private Role role;

    // ─── Identité ────────────────────────────────────────────────────────────
    @Email
    @NotBlank
    @Column(name = "email", nullable = false, unique = true)
    @Setter private String email;

    @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "phone format invalid")
    @Column(name = "phone", unique = true)
    @Setter private String phone;

    @NotBlank
    @Column(name = "password_hash", nullable = false)
    @Setter private String passwordHash;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false)
    @Setter private String firstName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false)
    @Setter private String lastName;

    @Column(name = "avatar_url")
    @Setter private String avatarUrl;

    @Pattern(regexp = "^(fr|en|ar)$", message = "language must be fr/en/ar")
    @Column(name = "language", nullable = false)
    @Setter private String language = "fr";

    @Column(name = "status", nullable = false)
    @Setter private String status = "active";

    // ─── Flags Spring Security (§2.1) ────────────────────────────────────────
    @Column(name = "account_non_expired", nullable = false)
    private boolean accountNonExpired = true;

    @Column(name = "account_non_locked", nullable = false)
    private boolean accountNonLocked = true;

    @Column(name = "credentials_non_expired", nullable = false)
    private boolean credentialsNonExpired = true;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    @Setter private Instant lastLoginAt;

    /**
     * Code de parrainage public stable (8 chars uppercase) — généré depuis l'UUID
     * via migration V14. Lecture seule au niveau API : non modifiable via PATCH.
     */
    @Column(name = "referral_code", unique = true)
    @Setter private String referralCode;

    public User(UUID id, Role role, String email, String passwordHash, String firstName, String lastName) {
        this.id = id;
        this.role = role;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    /** Mapping vers le DTO public exposé hors du module. */
    public UserDto toDto() {
        return new UserDto(
            id, tenantId, roleId,
            role != null ? role.getCode() : null,
            email, phone, firstName, lastName, avatarUrl, language, status,
            accountNonExpired, accountNonLocked, credentialsNonExpired, enabled,
            lastLoginAt, getCreatedAt(), referralCode
        );
    }

    // ─── equals / hashCode anti-proxy LAZY ───────────────────────────────────
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        User that = (User) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
