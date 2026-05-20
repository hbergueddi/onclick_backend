/**
 * Module {@code modules/analytics} — search + API management + webhooks §19
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.analytics",
    displayName = "modules/analytics",
    allowedDependencies = {"core.tenant", "audit", "exception", "security", "shared", "realtime"}
)
package com.onesley.oneclick.modules.analytics;

import org.springframework.modulith.ApplicationModule;
