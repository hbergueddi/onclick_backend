/**
 * Module {@code modules/community} — posts, commentaires, likes §8
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.community",
    displayName = "modules/community",
    allowedDependencies = {"core.identity", "audit", "exception", "security", "shared"}
)
package com.onesley.oneclick.modules.community;

import org.springframework.modulith.ApplicationModule;
