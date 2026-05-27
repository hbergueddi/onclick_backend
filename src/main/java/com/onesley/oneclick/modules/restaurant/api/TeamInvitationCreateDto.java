package com.onesley.oneclick.modules.restaurant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** DTO payload pour POST /api/team-invitations. */
public record TeamInvitationCreateDto(
    @NotNull UUID restaurantId,
    @NotBlank @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Size(max = 40) String phone,
    @Size(max = 40) String role
) {
}
