package com.onesley.oneclick.core.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de lecture pour User — exposé via l'API REST.
 *
 * <p>{@code passwordHash} jamais exposé (sécurité).
 */
public record UserDto(
    UUID id,
    UUID tenantId,
    UUID roleId,
    String roleCode,
    String email,
    String phone,
    String firstName,
    String lastName,
    String avatarUrl,
    String language,
    String status,
    boolean accountNonExpired,
    boolean accountNonLocked,
    boolean credentialsNonExpired,
    boolean enabled,
    Instant lastLoginAt,
    Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
            user.getId(),
            user.getTenantId(),
            user.getRoleId(),
            user.getRole() != null ? user.getRole().getCode() : null,
            user.getEmail(),
            user.getPhone(),
            user.getFirstName(),
            user.getLastName(),
            user.getAvatarUrl(),
            user.getLanguage(),
            user.getStatus(),
            user.isAccountNonExpired(),
            user.isAccountNonLocked(),
            user.isCredentialsNonExpired(),
            user.isEnabled(),
            user.getLastLoginAt(),
            user.getCreatedAt()
        );
    }
}
