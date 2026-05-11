/**
 * Module {@code modules/resource_booking} — réservation de ressources génériques (§10).
 *
 * <p>Modèle générique vs legacy PCC-specific (bookable_resources + resource_bookings +
 * loyalty_punch_cards). Couvre padel, spa, golf, coiffeur, gym, etc.
 */
@ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN, id = "modules.resource_booking", displayName = "modules/resource_booking")
package com.onesley.oneclick.modules.resource_booking;

import org.springframework.modulith.ApplicationModule;
