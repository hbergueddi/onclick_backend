/**
 * Umbrella module {@code modules} — regroupe les sous-modules métier
 * (restaurant, reservation, loyalty, promotion, community, social, event,
 * resource_booking, financial, payment, support, analytics).
 *
 * <p>{@link ApplicationModule.Type#OPEN} : ce parent module est ouvert pour
 * permettre les références croisées entre ses sous-modules ET depuis {@code core.*}
 * (ex: {@code modules.loyalty → modules.restaurant}, valide selon allowedDependencies).
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "modules (umbrella)")
package com.onesley.oneclick.modules;

import org.springframework.modulith.ApplicationModule;
