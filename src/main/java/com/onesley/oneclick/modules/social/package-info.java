/**
 * Module {@code modules/social} — amitiés et parrainages §8
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.social",
    displayName = "modules/social",
    allowedDependencies = {"core.identity", "audit", "exception", "security"}
)
package com.onesley.oneclick.modules.social;

import org.springframework.modulith.ApplicationModule;
