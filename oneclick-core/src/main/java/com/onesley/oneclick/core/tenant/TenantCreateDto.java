package com.onesley.oneclick.core.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TenantCreateDto(
    @NotBlank String name,
    @NotBlank @Pattern(regexp = "^[a-z0-9_-]+$") String slug
) {
}
