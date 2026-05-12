package com.onesley.oneclick.modules.event.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;
import com.onesley.oneclick.modules.event.internal.EventParticipation;

public final class EventDtos {

    private EventDtos() {}

    public record EventDto(UUID id, UUID tenantId, UUID restaurantId, String title, String description,
                           String eventType, Instant eventAt, Integer capacity, Instant createdAt) {
        public static EventDto from(Event e) {
            return new EventDto(e.getId(), e.getTenantId(), e.getRestaurantId(),
                e.getTitle(), e.getDescription(), e.getEventType(),
                e.getEventAt(), e.getCapacity(), e.getCreatedAt());
        }
    }

    public record EventCreateDto(
        @NotNull UUID tenantId,
        UUID restaurantId,
        @NotBlank String title,
        String description,
        String eventType,
        @NotNull Instant eventAt,
        Integer capacity
    ) {}

    public record ParticipationDto(UUID id, UUID eventId, UUID userId, String status, Instant createdAt) {
        public static ParticipationDto from(EventParticipation p) {
            return new ParticipationDto(p.getId(), p.getEventId(), p.getUserId(), p.getStatus(), p.getCreatedAt());
        }
    }

    public record ParticipationCreateDto(
        @NotNull UUID eventId,
        @NotNull UUID userId,
        @Pattern(regexp = "^(going|maybe|declined|attended)$") String status
    ) {}
}
