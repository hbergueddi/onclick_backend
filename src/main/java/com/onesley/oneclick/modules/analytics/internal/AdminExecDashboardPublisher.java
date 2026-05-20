package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.AdminStatsFullDto;
import com.onesley.oneclick.realtime.AbstractDashboardPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bug 37 — Publisher temps réel du dashboard exécutif admin.
 *
 * <p>Pousse {@link AdminStatsFullDto} (KPI plateforme : hero, deltas, tendances
 * 30j, funnel, top restos, cuisine, staff) sur {@value #TOPIC}. Toute la
 * mécanique (scheduled + gate + push-si-changé + push à l'abonnement) est
 * factorisée dans {@link AbstractDashboardPublisher}.
 *
 * <p>period null → snapshot plateforme par défaut. Topic autorisé SUPERADMIN
 * par {@code StompAuthChannelInterceptor}.
 */
@Component
public class AdminExecDashboardPublisher extends AbstractDashboardPublisher<AdminStatsFullDto> {

    public static final String TOPIC = "/topic/admin/exec";

    private final AdminStatsFullService statsService;

    public AdminExecDashboardPublisher(SimpMessagingTemplate messagingTemplate, AdminStatsFullService statsService) {
        super(messagingTemplate);
        this.statsService = statsService;
    }

    @Override
    protected String topic() {
        return TOPIC;
    }

    @Override
    protected AdminStatsFullDto computeSnapshot() {
        return statsService.compute(null);
    }
}
