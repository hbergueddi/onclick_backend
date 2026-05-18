package com.onesley.oneclick.modules.loyalty.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Item retourné par {@code GET /api/loyalty/enrollments/by-restaurant/{restaurantId}}
 * — N dernières inscriptions welcome d'un restaurant (UI : liste sous le wizard).
 *
 * <p>Source : {@code loyalty_transactions WHERE reason='welcome'} joint avec
 * {@code loyalty_accounts} pour le {@code client_id} et {@code users} pour le
 * nom du membre. Aggregation interne à {@code EnrollmentService} (pas de SQL
 * cross-module exposé).
 */
public record EnrollmentRecordDto(
    UUID transactionId,
    UUID clientId,
    String clientFirstName,
    String clientLastName,
    String clientEmail,
    int pointsGranted,
    Instant enrolledAt
) {
}
