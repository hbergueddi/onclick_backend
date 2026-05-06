package com.onesley.oneclick.permission;

/**
 * Les 7 types d'action permission du brief senior.
 *
 * <p>Le naming match exactement les valeurs en DB ({@code app_permissions.action}).
 */
public enum PermissionAction {
    READ,
    WRITE,
    UPDATE,
    DELETE,
    SHOW,
    UPLOAD,
    DOWNLOAD
}
