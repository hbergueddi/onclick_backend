/**
 * Module {@code modules/support} — tickets support client §13
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.support",
    displayName = "modules/support",
    allowedDependencies = {"core.identity", "audit", "exception", "security", "shared"}
)
package com.onesley.oneclick.modules.support;

import org.springframework.modulith.ApplicationModule;
