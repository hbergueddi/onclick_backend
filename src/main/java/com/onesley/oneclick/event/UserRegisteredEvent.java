package com.onesley.oneclick.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Émis quand un nouveau profil applicatif est créé (post-signup Supabase).
 *
 * <p>Listeners attendus en Phase 11+ :
 * <ul>
 *   <li>Email de bienvenue (via Resend)</li>
 *   <li>Insertion dans {@code admin_audit_log} (audit signup)</li>
 *   <li>Activation du parrainage si {@code referredById != null} (créditer 50 pts)</li>
 *   <li>Affecter le tenant_id si signup via whitelabel</li>
 * </ul>
 */
public record UserRegisteredEvent(
    UUID eventId,
    Instant occurredAt,
    UUID userId,
    String email,
    String firstName,
    String lastName,
    UUID referredById,
    UUID tenantId
) implements DomainEvent {

    public static UserRegisteredEvent of(
        UUID userId, String email, String firstName, String lastName,
        UUID referredById, UUID tenantId
    ) {
        return new UserRegisteredEvent(
            UUID.randomUUID(), Instant.now(),
            userId, email, firstName, lastName, referredById, tenantId
        );
    }

    @Override
    public String aggregateType() { return "profile"; }

    @Override
    public UUID aggregateId() { return userId; }
}
