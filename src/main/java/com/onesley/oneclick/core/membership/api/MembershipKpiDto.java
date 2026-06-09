package com.onesley.oneclick.core.membership.api;

import java.util.Map;
import java.util.UUID;

/**
 * KPIs temps réel des membres d'un programme (tenant) — poussés en <b>WebSocket/STOMP</b>
 * (P3, {@code MembershipKpiPublisher}, topic {@code /topic/admin/membership-kpis/{tenantId}}).
 *
 * <p>Sans horodatage volontairement : le publisher compare deux snapshots ({@code equals}) pour ne
 * pousser que sur changement — un timestamp les rendrait toujours différents.</p>
 *
 * @param tenantId     tenant concerné
 * @param totalActive  nombre de memberships actives
 * @param newLast7Days memberships actives rejointes dans les 7 derniers jours
 * @param byType       répartition active par {@code member_type} (clé "(non précisé)" si null)
 */
public record MembershipKpiDto(
    UUID tenantId,
    long totalActive,
    long newLast7Days,
    Map<String, Long> byType
) {
}
