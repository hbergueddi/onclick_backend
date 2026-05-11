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
 */
@ApplicationModule(displayName = "core/tenant")
package com.onesley.oneclick.core.tenant;

import org.springframework.modulith.ApplicationModule;
