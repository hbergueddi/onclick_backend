package com.onesley.oneclick.dto.loyalty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO pour {@code elite_applications} (généré par scripts/scaffold-jpa.mjs).
 */
public record EliteApplicationDto(
    UUID id,
    UUID userId,
    String fullName,
    String email,
    String phone,
    String motivation,
    String preferredTier,
    UUID sponsorUserId,
    String sponsorName,
    String status,
    String adminNote,
    UUID reviewedBy,
    Instant reviewedAt,
    Instant createdAt,
    Instant updatedAt,
    Integer age,
    String profession,
    String company,
    List<String> interests,
    Boolean hasOtherClubs,
    String otherClubsDetails,
    String city,
    String annualDiningBudget,
    String referralSource
) {
}
