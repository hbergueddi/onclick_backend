package com.onesley.oneclick.modules.store.api;

import com.onesley.oneclick.modules.store.internal.StoreOnboardingRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
        Instant createdAt,
        // ─── Champs enrollment legacy (V58) ───
        String budget,
        String description,
        String ownerRole,
        String ice,
        String ifNumber,
        String rc,
        String patente,
        Integer capacity,
        List<String> services
    ) {
        public static OnboardingRequestDto from(StoreOnboardingRequest r) {
            return new OnboardingRequestDto(
                r.getId(), r.getTenantId(), r.getRestaurantName(), r.getCuisine(),
                r.getCity(), r.getAddress(), r.getPhone(),
                r.getOwnerFirstName(), r.getOwnerLastName(), r.getOwnerEmail(), r.getOwnerPhone(),
                r.getStatus(), r.getRejectionReason(),
                r.getReviewedBy(), r.getReviewedAt(), r.getDecisionEmailSentAt(),
                r.getCreatedAt(),
                r.getBudget(), r.getDescription(), r.getOwnerRole(),
                r.getIce(), r.getIfNumber(), r.getRc(), r.getPatente(), r.getCapacity(),
                r.getServices() == null ? List.of() : Arrays.asList(r.getServices())
            );
        }
    }

    public record OnboardingCreateDto(
        UUID tenantId,
        @NotBlank @Size(min = 1, max = 128) String restaurantName,
        @Size(min = 1, max = 64) String cuisine,
        @Size(min = 1, max = 128) String city,
        @Size(min = 1, max = 256) String address,
        @Size(min = 1, max = 64) String phone,
        @NotBlank @Size(min = 1, max = 128) String ownerFirstName,
        @NotBlank @Size(min = 1, max = 128) String ownerLastName,
        @NotBlank @Email @Size(min = 1, max = 256) String ownerEmail,
        @Pattern(regexp = "^[+0-9\\s()-]*$", message = "phone format invalide")
        @Size(min = 1, max = 64) String ownerPhone,
        // ─── Champs enrollment legacy (V58) — optionnels, saisis au formulaire ───
        @Size(max = 8) String budget,
        @Size(max = 2000) String description,
        @Size(max = 64) String ownerRole,
        @Size(max = 32) String ice,
        @Size(max = 32) String ifNumber,
        @Size(max = 64) String rc,
        @Size(max = 64) String patente,
        @Min(0) Integer capacity,
        @Size(max = 20) List<String> services
    ) {}

    public record OnboardingDecisionDto(
        @Pattern(regexp = "approved|rejected", message = "status doit être 'approved' ou 'rejected'")
        @Size(min = 1, max = 64) String status,
        @Size(min = 1, max = 1024) String rejectionReason,
        UUID reviewedBy
    ) {}
}
