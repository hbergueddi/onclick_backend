package com.onesley.oneclick.entity.auth;

import com.onesley.oneclick.entity.shared.AppRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.user_roles} — association user ↔ rôle ({@code app_role}).
 *
 * <p>Pattern : <i>enum Postgres + jointure user vers Profile + sans timestamps</i>.
 *
 * <p>Caractéristiques mappées :
 * <ul>
 *   <li>PK simple {@code id uuid} avec default DB {@code gen_random_uuid()} →
 *       Hibernate ne fournit pas l'ID, c'est Postgres qui le génère via DEFAULT.</li>
 *   <li>{@code role} = enum Postgres {@code app_role} mappé via {@link AppRole},
 *       {@code @JdbcTypeCode(SqlTypes.NAMED_ENUM)} pour respecter le type natif PG
 *       (sinon Hibernate envoie un VARCHAR et PG refuse l'insert).</li>
 *   <li>UNIQUE (user_id, role) — déclarée pour qu'Hibernate connaisse la contrainte
 *       (déjà présente en DB via la migration baseline).</li>
 * </ul>
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code user_id} = FK {@code auth.users(id)} en DB. Vu que {@code profiles.id}
 *       est 1-1 avec {@code auth.users.id} (même UUID), on jointure logiquement
 *       vers {@link Profile} (pas vers auth.users qui n'est pas mappée).</li>
 * </ul>
 *
 * <p><b>Pas d'héritage</b> de {@link com.onesley.oneclick.audit.TimestampedEntity}
 * car la table n'a ni {@code created_at} ni {@code updated_at}.
 */
@Entity
@Table(
    name = "user_roles",
    uniqueConstraints = @UniqueConstraint(
        name = "user_roles_user_id_role_key",
        columnNames = {"user_id", "role"}
    )
)
public class UserRole {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Jointure user_id (FK auth.users.id, mappée vers Profile via shared PK) ─
    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Profile user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "app_role")
    private AppRole role;

    protected UserRole() {
        // JPA
    }

    public UserRole(UUID id, Profile user, AppRole role) {
        this.id = id;
        this.user = user;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    /** Raccourci read-only (issu de la colonne FK). Utiliser pour DTOs/projections. */
    public UUID getUserId() {
        return userId;
    }

    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Profile getUser() {
        return user;
    }

    public void setUser(Profile user) {
        this.user = user;
    }

    public AppRole getRole() {
        return role;
    }

    public void setRole(AppRole role) {
        this.role = role;
    }

    // ─── equals / hashCode anti-proxy LAZY ──────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        UserRole that = (UserRole) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
