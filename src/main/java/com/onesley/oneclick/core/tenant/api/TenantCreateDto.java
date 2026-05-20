package com.onesley.oneclick.core.tenant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record TenantCreateDto(
    @NotBlank @Size(min = 1, max = 128) String name,
    @NotBlank @Pattern(regexp = "^[a-z0-9_-]+$") @Size(min = 1, max = 64) String slug
) {
}
