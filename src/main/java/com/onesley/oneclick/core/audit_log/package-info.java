/**
 * Module {@code core/audit_log} — monitoring et audit centralisé (§15).
 *
 * <h3>Tables</h3>
 * <ul>
 *   <li>{@code audit_logs} — qui a fait quoi sur quelle entité (avec diff jsonb)</li>
 *   <li>{@code system_events} — domain events persistés (alternative event store)</li>
 *   <li>{@code error_logs} — erreurs applicatives (équivalent Sentry persisté)</li>
 *   <li>{@code job_executions} — statut des cron jobs / batch</li>
 * </ul>
 */
@ApplicationModule(displayName = "core/audit_log", allowedDependencies = {"core/identity", "core/tenant"})
package com.onesley.oneclick.core.audit_log;

import org.springframework.modulith.ApplicationModule;
