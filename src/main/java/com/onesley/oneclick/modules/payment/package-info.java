/**
 * Module {@code modules/payment} — paiements (carte, wallet, etc.) §12 — NOUVEAU module
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.payment",
    displayName = "modules/payment",
    allowedDependencies = {"audit", "exception", "security", "shared"}
)
package com.onesley.oneclick.modules.payment;

import org.springframework.modulith.ApplicationModule;
