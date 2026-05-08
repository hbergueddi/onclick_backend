package com.onesley.oneclick.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.staff_role_permissions} — matrice (staff_role × permission_id) → enabled.
 *
 * <p>Aucune jointure JPA : tous les "FK-like" sont en réalité des slugs textuels.
 *
 * <h3>Jointures JPA (passe 3 — décisions explicites)</h3>
 * <ul>
 *   <li>{@code permission_id} → <b>String slug</b> (ex: {@code "manage_team"},
 *       {@code "view_calendar"}). <b>Pas une FK UUID</b>. Aucune table de
 *       permissions normalisée — la liste vit dans le code applicatif.</li>
 *   <li>{@code staff_role} → idem, slug textuel correspondant à l'enum métier.</li>
 *   <li>{@code updated_by} → audit field, conserve UUID brut par convention
 *       (pas de jointure auto sur les audits, sinon Profile chargé partout).</li>
 * </ul>
 */
@Entity
@Table(name = "staff_role_permissions")
public class StaffRolePermission {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "staff_role", nullable = false)
    private String staffRole;

    @NotBlank
    @Column(name = "permission_id", nullable = false)
    private String permissionId;

    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Audit field : UUID brut (cf. Javadoc classe). */
    @Column(name = "updated_by")
    private UUID updatedBy;

    protected StaffRolePermission() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getStaffRole() { return staffRole; }
    public String getPermissionId() { return permissionId; }
    public Boolean getEnabled() { return enabled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }

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
        StaffRolePermission that = (StaffRolePermission) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
