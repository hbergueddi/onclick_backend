/**
 * Module {@code modules/promotion} — offres / promotions par restaurant §7
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.promotion",
    displayName = "modules/promotion",
    allowedDependencies = {"audit", "exception", "search", "shared"}
)
package com.onesley.oneclick.modules.promotion;

import org.springframework.modulith.ApplicationModule;
