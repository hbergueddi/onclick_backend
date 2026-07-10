/**
 * Module {@code modules/reservation} — workflow réservation (§5).
 *
 * <h3>Tables</h3>
 * <ul>
 *   <li>{@code reservations} — réservation principale (reservation_at unifié) ;
 *       {@code late_cancellation} + {@code no_show_marked_at} (Feature #4)</li>
 *   <li>{@code reservation_guests} — invités (user OneClick OU nom libre)</li>
 *   <li>{@code reservation_status_histories} — audit workflow des changements</li>
 *   <li>{@code booking_rules} — règles par restaurant (max guest, slot duration)</li>
 *   <li>{@code no_show_disputes} — contestation d'un no_show (Feature #3, domaine dispute)</li>
 * </ul>
 *
 * <h3>Workflow status</h3>
 * <p>{@code pending → confirmed | refused | counter_proposed | cancelled}, puis
 * {@code confirmed → honored | no_show | cancelled}.
 *
 * <h3>Réputation client — frontière Modulith (Feature #4 + #3)</h3>
 * <p>Le module N'APPELLE PAS loyalty directement. Il publie des events
 * ({@link com.onesley.oneclick.shared.events.ReservationStatusChangedEvent},
 * {@link com.onesley.oneclick.shared.events.NoShowDisputeResolvedEvent}) que le module
 * loyalty consomme ({@code ReservationRatingListener}) pour appliquer/reverser la
 * pénalité de réputation. Dashboard contestations temps réel via STOMP
 * ({@code DisputeDashboardPublisher} → {@code /topic/disputes}).
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.reservation",
    displayName = "modules/reservation",
    allowedDependencies = {"core.identity", "core.tenant", "core.ai", "audit", "exception", "search", "security", "shared", "realtime"}
)
package com.onesley.oneclick.modules.reservation;

import org.springframework.modulith.ApplicationModule;
