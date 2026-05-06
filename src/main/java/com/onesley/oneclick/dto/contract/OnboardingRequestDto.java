package com.onesley.oneclick.dto.contract;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO pour {@code onboarding_requests} (généré par scripts/scaffold-jpa.mjs).
 */
public record OnboardingRequestDto(
    UUID id,
    String status,
    String restaurantName,
    String city,
    String address,
    String phone,
    String cuisine,
    String budget,
    String description,
    String firstName,
    String lastName,
    String email,
    String contactPhone,
    String role,
    String ice,
    String ifNumber,
    String rc,
    String patente,
    Integer capacity,
    List<String> services,
    UUID reviewedBy,
    Instant reviewedAt,
    String rejectionReason,
    Instant createdAt,
    Instant updatedAt,
    UUID tenantId
) {
}
