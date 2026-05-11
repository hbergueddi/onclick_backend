package com.onesley.oneclick.modules.reservation;

import java.time.Instant;
import java.util.UUID;

public record ReservationDto(
    UUID id,
    UUID tenantId,
    UUID clientId,
    UUID restaurantId,
    UUID tableId,
    UUID serviceId,
    Instant reservationAt,
    Integer guestCount,
    String status,
    String notes,
    Instant createdAt
) {
    public static ReservationDto from(Reservation r) {
        return new ReservationDto(
            r.getId(), r.getTenantId(), r.getClientId(), r.getRestaurantId(),
            r.getTableId(), r.getServiceId(),
            r.getReservationAt(), r.getGuestCount(),
            r.getStatus(), r.getNotes(), r.getCreatedAt()
        );
    }
}
