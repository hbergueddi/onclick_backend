package com.onesley.oneclick.modules.restaurant.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO payload pour PATCH /api/team-invitations/{id} — partial update.
 * {@code status} ∈ pending|accepted|disabled (EN).
 */
public record TeamInvitationPatchDto(
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Size(max = 40) String phone,
    @Size(max = 40) String role,
    @Pattern(regexp = "pending|accepted|disabled") String status
) {
}
