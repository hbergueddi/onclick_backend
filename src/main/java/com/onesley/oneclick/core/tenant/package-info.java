/**
 * Module {@code core/tenant} — multi-tenant racine + branding + features + config légale.
 *
 * <h3>Tables</h3>
 * <ul>
 *   <li>{@code tenants} — 1 ligne par marque whitelabel (OneClick, HOMU, PCC, Restopro)</li>
 *   <li>{@code tenant_brandings} — logo, couleurs, custom domain (1-1 avec tenant)</li>
 *   <li>{@code tenant_features} — feature flags par tenant</li>
 *   <li>{@code company_settings} — config légale Maroc (ICE, RIB, TVA) (1-1 avec tenant)</li>
 * </ul>
 *
 * <h3>API exposée (Type.CLOSED — Sprint C.3)</h3>
 * <ul>
 *   <li>{@code api/Tenant} — Entity de plateforme, exposée pour @ManyToOne cross-module</li>
 *   <li>{@code api/TenantDto, TenantCreateDto} — DTOs publics</li>
 * </ul>
 * Les entities {@code internal/TenantBranding, TenantFeature, CompanySettings} restent privées.
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    id = "core.tenant",
    displayName = "core/tenant"
)
package com.onesley.oneclick.core.tenant;

import org.springframework.modulith.ApplicationModule;
