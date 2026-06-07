package com.onesley.oneclick.core.identity.api;

import jakarta.validation.constraints.Pattern;

/**
 * Payload {@code PATCH /api/users/{id}/pcc-member-type} (axe H).
 *
 * <p>{@code memberType} : {@code resident} | {@code non_resident} | {@code null}
 * ({@code null} retire le statut de membre PCC). {@code @Pattern} ne valide que les
 * valeurs non-null → null accepté (clear).
 */
public record PccMemberTypeUpdateDto(
    @Pattern(regexp = "^(resident|non_resident)$") String memberType
) {
}
