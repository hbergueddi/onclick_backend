/**
 * Module {@code modules/resource_booking} — réservation de ressources génériques (§10).
 *
 * <p>Modèle générique vs legacy PCC-specific (bookable_resources + resource_bookings +
 * loyalty_punch_cards). Couvre padel, spa, golf, coiffeur, gym, etc.
 */
@ApplicationModule(displayName = "modules/resource_booking",
    allowedDependencies = {"core/identity", "core/tenant"})
package com.onesley.oneclick.modules.resource_booking;

import org.springframework.modulith.ApplicationModule;
