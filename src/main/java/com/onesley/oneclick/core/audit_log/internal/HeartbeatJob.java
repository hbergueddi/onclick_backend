package com.onesley.oneclick.core.audit_log.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job heartbeat — sert de canari pour vérifier que :
 * <ul>
 *   <li>{@code @EnableScheduling} fonctionne</li>
 *   <li>{@link JobExecutionAspect} intercepte bien les méthodes {@code @Scheduled}</li>
 *   <li>Les rows arrivent dans {@code job_executions} (§15 spec senior)</li>
 * </ul>
 *
 * <p>Une exécution par minute = ~1440 rows / jour. Suffisant pour valider sans
 * polluer la table.
 */
@Component
public class HeartbeatJob {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatJob.class);

    @Scheduled(fixedRateString = "${app.jobs.heartbeat-rate-ms:60000}", initialDelay = 5000)
    public void heartbeat() {
        log.debug("Heartbeat tick — JobExecutionAspect should append row in job_executions");
    }
}
