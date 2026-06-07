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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
    
    @Column(name = "email", nullable = false, unique = true, length = 256)
    @Setter private String email;

    @Column(name = "phone", unique = true, length = 64)
    @Setter private String phone;

    @Column(name = "password_hash", nullable = false, length = 128)
    @Setter private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 128)
    @Setter private String firstName;

    @Column(name = "last_name", nullable = false, length = 128)
    @Setter private String lastName;

    @Column(name = "avatar_url", length = 512)
    @Setter private String avatarUrl;

    /** Ville du user (PersonalInfo Pocket) — V64, optionnel, éditable via PATCH /me. */
    @Column(name = "city", length = 128)
    @Setter private String city;

    /**
     * Allergènes du user (profil client) — V65, parité legacy {@code profiles.allergens}.
     * Liste de slugs EU (gluten, lactose, …). Éditable via PATCH /me (UPDATE:PROFILE) ;
     * visible par le staff sur la fiche réservation (read-view). Mappé directement sur
     * la colonne PostgreSQL {@code text[]} (même pattern que {@code Restaurant.tags}).
     * NOT NULL côté DB (DEFAULT '{}') → jamais null, on initialise à un tableau vide.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allergens", columnDefinition = "text[]")
    @Setter private String[] allergens = new String[0];

    @Column(name = "language", nullable = false, length = 64)
    @Setter private String language = "fr";

    /**
     * Type de membre PCC (axe H — V79) : {@code resident} | {@code non_resident} | null.
     * Pilote la remise PCC (résidant -20% / non-résidant -15%). Posé par un admin
     * (PATCH /api/users/{id}/pcc-member-type, UPDATE:USERS) ; NULL = non-membre.
     */
    @Column(name = "pcc_member_type", length = 32)
    @Setter private String pccMemberType;

    @Column(name = "status", nullable = false, length = 64)
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
    @Column(name = "referral_code", unique = true, length = 64)
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
            email, phone, firstName, lastName, avatarUrl, city,
            allergens == null ? List.of() : Arrays.asList(allergens),
            language, status,
            accountNonExpired, accountNonLocked, credentialsNonExpired, enabled,
            lastLoginAt, getCreatedAt(), referralCode, pccMemberType
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
