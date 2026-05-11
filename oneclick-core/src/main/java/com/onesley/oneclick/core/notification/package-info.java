/**
 * Module {@code core/notification} — push, in-app, email multi-canal (§7).
 *
 * <h3>Tables</h3>
 * <ul>
 *   <li>{@code notifications} — notif unitaire avec channel explicite (push/email/inapp/sms)</li>
 *   <li>{@code notification_campaigns} — campagnes marketing programmées</li>
 *   <li>{@code device_tokens} — tokens FCM/APNs par device pour push mobile</li>
 * </ul>
 */
// Note Phase 2 : on autorise aussi 'shared::events' pour consommer les events
// publiés par les autres modules (ReservationCreatedEvent, LoyaltyEarnedEvent, ...)
// sans créer de dépendance directe sur les modules émetteurs.
@ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN, id = "core.notification", displayName = "core/notification")
package com.onesley.oneclick.core.notification;

import org.springframework.modulith.ApplicationModule;
