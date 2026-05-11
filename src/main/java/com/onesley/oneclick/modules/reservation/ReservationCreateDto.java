package com.onesley.oneclick.modules.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ReservationCreateDto(
    @NotNull UUID tenantId,
    @NotNull UUID clientId,
    @NotNull UUID restaurantId,
    UUID tableId,
    UUID serviceId,
    @NotNull Instant reservationAt,
    @NotNull @Min(1) Integer guestCount,
    String notes
) {
}
