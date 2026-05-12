package com.onesley.oneclick.modules.resource_booking.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.onesley.oneclick.modules.resource_booking.internal.Resource;
import com.onesley.oneclick.modules.resource_booking.internal.ResourceBooking;
import com.onesley.oneclick.modules.resource_booking.internal.ResourceBookingGuest;
import com.onesley.oneclick.modules.resource_booking.internal.ResourcePricing;

public final class ResourceBookingDtos {

    private ResourceBookingDtos() {}

    // ─── Resource ────────────────────────────────────────────────────────────

    public record ResourceDto(UUID id, UUID tenantId, String resourceType, String name, String description,
                              Integer capacity, boolean enabled, Instant createdAt) {
        public static ResourceDto from(Resource r) {
            return new ResourceDto(r.getId(), r.getTenantId(), r.getResourceType(), r.getName(),
                r.getDescription(), r.getCapacity(), r.isEnabled(), r.getCreatedAt());
        }
    }

    public record ResourceCreateDto(
        @NotNull UUID tenantId,
        @NotBlank String resourceType,
        @NotBlank String name,
        String description,
        Integer capacity
    ) {}

    // ─── Pricing ─────────────────────────────────────────────────────────────

    public record PricingDto(UUID id, UUID resourceId, String name, BigDecimal price, Integer durationMinutes,
                             boolean enabled, Instant createdAt) {
        public static PricingDto from(ResourcePricing p) {
            return new PricingDto(p.getId(), p.getResourceId(), p.getName(), p.getPrice(),
                p.getDurationMinutes(), p.isEnabled(), p.getCreatedAt());
        }
    }

    public record PricingCreateDto(
        @NotNull UUID resourceId,
        @NotBlank String name,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        Integer durationMinutes
    ) {}

    // ─── Booking ─────────────────────────────────────────────────────────────

    public record BookingDto(UUID id, UUID resourceId, UUID organizerId, UUID pricingId, Instant startAt,
                             Instant endAt, String status, String notes, Instant createdAt) {
        public static BookingDto from(ResourceBooking b) {
            return new BookingDto(b.getId(), b.getResourceId(), b.getOrganizerId(), b.getPricingId(),
                b.getStartAt(), b.getEndAt(), b.getStatus(), b.getNotes(), b.getCreatedAt());
        }
    }

    public record BookingCreateDto(
        @NotNull UUID resourceId,
        @NotNull UUID organizerId,
        UUID pricingId,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @Pattern(regexp = "^(pending|confirmed|cancelled|no_show|completed)$") String status,
        String notes
    ) {}

    public record BookingUpdateDto(
        @Pattern(regexp = "^(pending|confirmed|cancelled|no_show|completed)$") String status,
        String notes
    ) {}

    // ─── Guest ───────────────────────────────────────────────────────────────

    public record GuestDto(UUID id, UUID bookingId, UUID guestUserId, String guestName, Instant createdAt) {
        public static GuestDto from(ResourceBookingGuest g) {
            return new GuestDto(g.getId(), g.getBookingId(), g.getGuestUserId(), g.getGuestName(), g.getCreatedAt());
        }
    }

    public record GuestCreateDto(
        @NotNull UUID bookingId,
        UUID guestUserId,
        String guestName
    ) {}
}
