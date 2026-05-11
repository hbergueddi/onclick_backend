package com.onesley.oneclick.shared.events;


import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un nouveau user s'inscrit.
 *
 * <p>Consommé par :
 * <ul>
 *   <li>notification-service → email/push de bienvenue</li>
 *   <li>loyalty-service → création auto compte loyalty si role=CLIENT (futur)</li>
 *   <li>analytics → KPI inscriptions (futur)</li>
 * </ul>
 */
public record UserRegisteredEvent(
    UUID userId,
    UUID tenantId,
    String email,
    String firstName,
    String lastName,
    String roleCode,
    String language,
    Instant occurredAt
) {
}
