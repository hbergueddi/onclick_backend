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
public class User extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Tenant (nullable pour admins globaux) ───────────────────────────────
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private com.onesley.oneclick.core.tenant.api.Tenant tenant;

    // ─── Rôle (1 seul, RBAC simplifié) ───────────────────────────────────────
    @Column(name = "role_id", nullable = false, insertable = false, updatable = false)
    private UUID roleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    // ─── Identité ────────────────────────────────────────────────────────────
    @Email
    @NotBlank
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "phone format invalid")
    @Column(name = "phone", unique = true)
    private String phone;

    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Pattern(regexp = "^(fr|en|ar)$", message = "language must be fr/en/ar")
    @Column(name = "language", nullable = false)
    private String language = "fr";

    @Column(name = "status", nullable = false)
    private String status = "active";

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
    private Instant lastLoginAt;

    protected User() {
        // JPA
    }

    public User(UUID id, Role role, String email, String passwordHash, String firstName, String lastName) {
        this.id = id;
        this.role = role;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public com.onesley.oneclick.core.tenant.api.Tenant getTenant() { return tenant; }
    public void setTenant(com.onesley.oneclick.core.tenant.api.Tenant tenant) { this.tenant = tenant; }
    public UUID getRoleId() { return roleId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isAccountNonExpired() { return accountNonExpired; }
    public boolean isAccountNonLocked() { return accountNonLocked; }
    public boolean isCredentialsNonExpired() { return credentialsNonExpired; }
    public boolean isEnabled() { return enabled; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    /** Mapping vers le DTO public exposé hors du module. */
    public UserDto toDto() {
        return new UserDto(
            id, tenantId, roleId,
            role != null ? role.getCode() : null,
            email, phone, firstName, lastName, avatarUrl, language, status,
            accountNonExpired, accountNonLocked, credentialsNonExpired, enabled,
            lastLoginAt, getCreatedAt()
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
