package com.onesley.oneclick.dto.reservation;

import com.onesley.oneclick.entity.reservation.ReservationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationDto(
    UUID id,
    UUID clientId,
    UUID restaurantId,
    LocalDate date,
    String heure,
    Integer couverts,
    String zone,
    Integer tableNum,
    String service,
    ReservationStatus status,
    String notes,
    String refusalReason,
    LocalDate proposedDate,
    String proposedHeure,
    Instant proposalExpiresAt,
    String cancellationReason,
    Instant noShowMarkedAt,
    Instant noShowPenaltyAppliedAt,
    Boolean lateCancellation,
    Instant reminderJ1SentAt,
    Instant reminderH2SentAt,
    Instant createdAt,
    Instant updatedAt
) {
}
