package com.onesley.oneclick.entity.auth;

/**
 * Enum {@code public.app_role} — rôles métier OneClick.
 *
 * <p>Les valeurs sont sérialisées en lowercase (matching exact avec l'enum DB
 * Postgres) via {@code @Enumerated(EnumType.STRING)} sur les colonnes qui
 * référencent ce type.
 */
public enum AppRole {
    admin,
    restaurateur,
    client,
    tenant_admin
}
