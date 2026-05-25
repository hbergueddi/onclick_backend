/**
 * Module {@code modules/event} — événements §9
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.event",
    displayName = "modules/event",
    allowedDependencies = {"core.identity", "core.tenant", "audit", "exception", "shared", "security"}
)
package com.onesley.oneclick.modules.event;

import org.springframework.modulith.ApplicationModule;
