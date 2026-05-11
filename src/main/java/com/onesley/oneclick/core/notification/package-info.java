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
@ApplicationModule(displayName = "core/notification", allowedDependencies = {"core/identity", "core/tenant"})
package com.onesley.oneclick.core.notification;

import org.springframework.modulith.ApplicationModule;
