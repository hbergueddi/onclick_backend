package com.onesley.oneclick.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.staff_role_permissions} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : entité simple.
 */
@Entity
@Table(name = "staff_role_permissions")
public class StaffRolePermission {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "staff_role", nullable = false)
    private String staffRole;

    @Column(name = "permission_id", nullable = false)
    private String permissionId;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
}
