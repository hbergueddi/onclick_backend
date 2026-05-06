package com.onesley.oneclick.permission;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Entité {@code public.app_permissions} — matrice (app_role × menu × action).
 *
 * <p>Lue intégralement au boot Spring par {@link PermissionsService} et cachée
 * en mémoire. La table fait < 200 lignes (4 rôles × 12 menus × 7 actions
 * théoriques = 336 max), pas besoin de pagination.
 */
@Entity
@Table(name = "app_permissions")
public class AppPermission extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "app_role", nullable = false)
    private String appRole;

    @Column(name = "menu", nullable = false)
    private String menu;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "granted", nullable = false)
    private Boolean granted = true;

    protected AppPermission() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getAppRole() { return appRole; }
    public String getMenu() { return menu; }
    public String getAction() { return action; }
    public Boolean getGranted() { return granted; }
}
