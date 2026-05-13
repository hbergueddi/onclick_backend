/**
 * Module {@code modules/oneclickhi} — whitelabel HR/payroll invoicing.
 *
 * <p>Module dédié aux factures whitelabel "OneClick HI" (HR Integration) destinées
 * aux groupes corporate. Isolé de {@code modules/financial} (qui couvre les
 * factures de commission partenaires standards).
 *
 * <p>Sprint H — création initiale.
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.oneclickhi",
    displayName = "modules/oneclickhi",
    allowedDependencies = {"core.tenant", "audit", "exception", "security", "shared"}
)
package com.onesley.oneclick.modules.oneclickhi;

import org.springframework.modulith.ApplicationModule;
