package com.onesley.oneclick.core.tenant.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTOs d'administration tenant (portail SUPERADMIN) — exposés par {@code TenantController}.
 *
 * <p>Le portail tenant-admin est <b>SUPERADMIN-only</b> (décision projet) : pas d'ABAC self-scope
 * ni d'impersonation — un administrateur plateforme gère les tenants par id via
 * {@code hasAuthority('VERB:TENANTS')}. Records immutables + Bean Validation sur les entrées.</p>
 */
public final class TenantAdminDtos {

    private TenantAdminDtos() {}

    /** Statuts valides d'un tenant (CHECK tenants_status_check V1 ; regex + garde-fou service). */
    public static final String STATUS_REGEX = "^(active|paused|archived)$";

    /**
     * Mise à jour partielle d'un tenant (PATCH). {@code name} / {@code status} optionnels
     * (null = inchangé). Le {@code slug} est immuable (clé de routing whitelabel) — non modifiable.
     */
    public record TenantUpdateDto(
        @Size(min = 2, max = 128) String name,
        @Pattern(regexp = STATUS_REGEX, message = "status invalide (active|paused|archived)") String status
    ) {}

    /** Branding visuel d'un tenant (lecture). {@code tenantId} = PK 1-1 (@MapsId). */
    public record TenantBrandingDto(
        UUID tenantId,
        String logoUrl,
        String primaryColor,
        String accentColor,
        String customDomain,
        // V74 — parité 1:1 éditeur branding.
        String backgroundColor,
        String logoDarkUrl,
        String faviconUrl,
        String tagline,
        String appNameWin,
        String appNameStore
    ) {}

    /**
     * Remplacement du branding (PUT). Tous les champs sont posés tels quels (null = effacé) —
     * le front envoie l'objet complet. {@code customDomain} unique en base.
     */
    public record TenantBrandingUpdateDto(
        @Size(max = 512) String logoUrl,
        @Size(max = 64) String primaryColor,
        @Size(max = 64) String accentColor,
        @Size(max = 512) String customDomain,
        @Size(max = 64) String backgroundColor,
        @Size(max = 512) String logoDarkUrl,
        @Size(max = 512) String faviconUrl,
        @Size(max = 256) String tagline,
        @Size(max = 64) String appNameWin,
        @Size(max = 64) String appNameStore
    ) {}

    /** Un feature flag d'un tenant (lecture). */
    public record TenantFeatureDto(
        String featureCode,
        boolean enabled
    ) {}

    /** Activation / désactivation d'un feature flag (PUT). */
    public record TenantFeatureToggleDto(
        @NotNull Boolean enabled
    ) {}
}
