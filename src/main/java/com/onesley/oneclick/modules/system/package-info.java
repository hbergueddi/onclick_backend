/**
 * Module {@code modules/system} — monitoring système, alertes, quota audit.
 *
 * <p>Centralise les tables d'audit système : {@code system_health_checks}
 * (heartbeats jobs), {@code system_alert_rules} (config admin), {@code system_alerts}
 * (alertes déclenchées), {@code quota_change_logs} (audit changement quotas).
 *
 * <p>Sprint H — création initiale.
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.system",
    displayName = "modules/system",
    allowedDependencies = {"audit", "exception", "security", "shared", "realtime"}
)
package com.onesley.oneclick.modules.system;

import org.springframework.modulith.ApplicationModule;
