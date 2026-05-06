package com.onesley.oneclick.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Émis quand un profil est mis à jour (PATCH).
 *
 * <p>Listeners typiques :
 * <ul>
 *   <li>Audit log (qui a modifié quoi quand)</li>
 *   <li>Re-cache du profil sur Redis (Phase 11+)</li>
 *   <li>Re-broadcast Sentry breadcrumb avec le nouveau city / language</li>
 * </ul>
 */
public record ProfileUpdatedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID userId
) implements DomainEvent {

    public static ProfileUpdatedEvent of(UUID userId) {
        return new ProfileUpdatedEvent(UUID.randomUUID(), Instant.now(), userId);
    }

    @Override
    public String aggregateType() { return "profile"; }

    @Override
    public UUID aggregateId() { return userId; }
}
