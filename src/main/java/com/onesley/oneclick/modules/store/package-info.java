/**
 * Module {@code modules/store} — workflow d'onboarding restaurants.
 *
 * <p>Gère les demandes d'inscription Store : capture du formulaire (nom resto,
 * owner, ville, etc.) → review admin → approval/rejection → création resto +
 * compte owner (via {@code modules/restaurant}).
 *
 * <p>Sprint I.3 — création initiale (port EF send-onboarding-request +
 * send-onboarding-decision).
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.store",
    displayName = "modules/store",
    allowedDependencies = {"core.tenant", "core.notification", "audit", "exception", "security", "shared"}
)
package com.onesley.oneclick.modules.store;

import org.springframework.modulith.ApplicationModule;
