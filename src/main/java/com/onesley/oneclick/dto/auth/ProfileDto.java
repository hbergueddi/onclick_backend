package com.onesley.oneclick.dto.auth;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de lecture pour {@code profiles}.
 *
 * <p>Expose toutes les colonnes utilisateur, à l'exception des FKs inversées
 * (réservations, points, etc. — récupérées via leurs propres endpoints).
 */
public record ProfileDto(
    UUID id,
    String firstName,
    String lastName,
    String phone,
    String city,
    String avatarUrl,
    String referralCode,
    BigDecimal reliabilityScore,
    Instant scoreUpdatedAt,
    String email,
    UUID tenantGroupId,
    UUID tenantId,
    String communityCoverUrl,
    List<String> allergens,
    String language,
    Instant createdAt,
    Instant updatedAt
) {
}
