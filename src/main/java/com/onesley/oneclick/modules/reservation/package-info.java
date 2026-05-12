/**
 * Module {@code modules/reservation} — workflow réservation (§5).
 *
 * <h3>Tables</h3>
 * <ul>
 *   <li>{@code reservations} — réservation principale (reservation_at unifié)</li>
 *   <li>{@code reservation_guests} — invités (user OneClick OU nom libre)</li>
 *   <li>{@code reservation_status_histories} — audit workflow des changements</li>
 *   <li>{@code booking_rules} — règles par restaurant (max guest, slot duration)</li>
 * </ul>
 *
 * <h3>Workflow status</h3>
 * <p>{@code pending → confirmed | refused | counter_proposed | cancelled}, puis
 * {@code confirmed → honored | no_show | cancelled}.
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.reservation",
    displayName = "modules/reservation",
    allowedDependencies = {"core.identity", "core.tenant", "audit", "exception", "search", "security", "shared"}
)
package com.onesley.oneclick.modules.reservation;

import org.springframework.modulith.ApplicationModule;
