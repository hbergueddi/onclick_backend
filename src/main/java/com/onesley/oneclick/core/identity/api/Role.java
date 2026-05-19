package com.onesley.oneclick.core.identity.api;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.proxy.HibernateProxy;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Rôle applicatif — RBAC simplifié, 1 user = 1 role.
 *
 * <p>Codes attendus : {@code ADMIN}, {@code RESTAURATEUR}, {@code STAFF},
 * {@code CLIENT}, {@code TENANT_ADMIN}, ...
 */
@Entity
@Table(name = "roles")
public class Role extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 64)
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @NotBlank
    @Size(max = 128)
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Bug 34 (UserDetails) — Permissions du rôle (relation inverse de Permission.role).
     * Chargé en LAZY ; les consumers qui ont besoin de tout le graphe (ex:
     * {@code UserRepository.findByIdWithRoleAndPermissions}) utilisent
     * {@code JOIN FETCH r.permissions} pour anticiper et éviter le N+1.
     *
     * <p>Set utilisé (pas List) : contrainte UNIQUE (role_id, menu_id, action_id)
     * en DB garantit l'unicité, et l'ordre n'est pas significatif pour le RBAC.
     */
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
        // JPA
    }

    public Role(UUID id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<Permission> getPermissions() { return permissions; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        Role that = (Role) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
