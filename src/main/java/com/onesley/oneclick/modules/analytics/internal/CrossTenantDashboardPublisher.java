package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.CrossTenantDtos.CrossTenantStatsDto;
import com.onesley.oneclick.realtime.AbstractDashboardPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher temps réel STOMP du CrossTenantDashboard (C3, vue super-admin globale).
 *
 * <p>Calque {@link AbstractDashboardPublisher} (recompute périodique borné, push si changé, gate
 * sessions, push immédiat à l'abonnement). Snapshot = {@link CrossTenantStatsDto} sur la fenêtre
 * par défaut 30 jours. Topic partagé {@code /topic/admin/cross-tenant} : vue plateforme globale
 * (cross-tenant), comme {@code AdminExecDashboardPublisher} — l'accès est borné côté abonnement par
 * la sécurité (seul un SUPERADMIN avec VIEW:TENANTS atteint la page qui s'abonne).</p>
 */
@Component
public class CrossTenantDashboardPublisher extends AbstractDashboardPublisher<CrossTenantStatsDto> {

    static final String TOPIC = "/topic/admin/cross-tenant";

    private final CrossTenantStatsService service;

    public CrossTenantDashboardPublisher(SimpMessagingTemplate messagingTemplate,
                                         CrossTenantStatsService service) {
        super(messagingTemplate);
        this.service = service;
    }

    @Override
    protected String topic() {
        return TOPIC;
    }

    @Override
    protected CrossTenantStatsDto computeSnapshot() {
        return service.compute(30);
    }
}
