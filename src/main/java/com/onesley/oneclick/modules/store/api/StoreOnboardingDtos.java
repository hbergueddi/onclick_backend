package com.onesley.oneclick.modules.store.api;

import com.onesley.oneclick.modules.store.internal.StoreOnboardingRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

public final class StoreOnboardingDtos {

    private StoreOnboardingDtos() {}

    public record OnboardingRequestDto(
        UUID id,
        UUID tenantId,
        String restaurantName,
        String cuisine,
        String city,
        String address,
        String phone,
        String ownerFirstName,
        String ownerLastName,
        String ownerEmail,
        String ownerPhone,
        String status,
        String rejectionReason,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant decisionEmailSentAt,
        Instant createdAt
    ) {
        public static OnboardingRequestDto from(StoreOnboardingRequest r) {
            return new OnboardingRequestDto(
                r.getId(), r.getTenantId(), r.getRestaurantName(), r.getCuisine(),
                r.getCity(), r.getAddress(), r.getPhone(),
                r.getOwnerFirstName(), r.getOwnerLastName(), r.getOwnerEmail(), r.getOwnerPhone(),
                r.getStatus(), r.getRejectionReason(),
                r.getReviewedBy(), r.getReviewedAt(), r.getDecisionEmailSentAt(),
                r.getCreatedAt()
            );
        }
    }

    public record OnboardingCreateDto(
        UUID tenantId,
        @NotBlank String restaurantName,
        String cuisine,
        String city,
        String address,
        String phone,
        @NotBlank String ownerFirstName,
        @NotBlank String ownerLastName,
        @NotBlank @Email String ownerEmail,
        @Pattern(regexp = "^[+0-9\\s()-]*$", message = "phone format invalide")
        String ownerPhone
    ) {}

    public record OnboardingDecisionDto(
        @Pattern(regexp = "approved|rejected", message = "status doit être 'approved' ou 'rejected'")
        String status,
        String rejectionReason,
        UUID reviewedBy
    ) {}
}
