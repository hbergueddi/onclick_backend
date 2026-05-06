package com.onesley.oneclick.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Entité {@code public.user_roles} — association user ↔ rôle.
 *
 * <p>Pattern pilote : <i>enum Postgres + FK auth.users + sans timestamps</i>.
 *
 * <p>Caractéristiques mappées :
 * <ul>
 *   <li>PK simple {@code id uuid} avec default DB {@code gen_random_uuid()} →
 *       Hibernate ne fournit pas l'ID, c'est Postgres qui le génère via DEFAULT</li>
 *   <li>{@code user_id} = FK vers {@code auth.users(id)} (gérée comme UUID brut, pas
 *       de @ManyToOne — auth.users est un schéma système Supabase non mappé)</li>
 *   <li>{@code role} = enum Postgres {@code app_role} mappé via {@link AppRole},
 *       {@code @JdbcTypeCode(SqlTypes.NAMED_ENUM)} pour respecter le type natif PG
 *       (sinon Hibernate envoie un VARCHAR et PG refuse l'insert)</li>
 *   <li>UNIQUE (user_id, role) — déclaré pour qu'Hibernate connaisse la contrainte
 *       (déjà présente en DB via la migration baseline)</li>
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

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "app_role")
    private AppRole role;

    protected UserRole() {
        // JPA
    }

    public UserRole(UUID id, UUID userId, AppRole role) {
        this.id = id;
        this.userId = userId;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AppRole getRole() {
        return role;
    }

    public void setRole(AppRole role) {
        this.role = role;
    }
}
