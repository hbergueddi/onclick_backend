package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Administrateur d'un tenant (portail tenant-admin C2). Un couple (tenant_id, user_id) est UNIQUE.
 *
 * <p>Table {@code tenant_admins} (V75). Clé surrogate {@code id} + UNIQUE(tenant_id, user_id)
 * (pattern {@code TenantFeature}). Le nom/contact de l'utilisateur n'est pas dénormalisé ici —
 * résolu à la lecture via {@code UserDirectoryApi} (core.identity). {@code role} ∈
 * {@code owner|admin|viewer} (CHECK en base).</p>
 */
@Entity
@Table(
    name = "tenant_admins",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantAdmin extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** {@code owner} / {@code admin} / {@code viewer} — CHECK en base. Mutable (changement de rôle). */
    @Column(name = "role", nullable = false, length = 16)
    @Setter private String role;

    /** SUPERADMIN ayant ajouté cet admin (traçabilité). Null si l'inviteur a été supprimé. */
    @Column(name = "invited_by", updatable = false)
    private UUID invitedBy;

    public TenantAdmin(UUID id, UUID tenantId, UUID userId, String role, UUID invitedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.role = role;
        this.invitedBy = invitedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((TenantAdmin) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
