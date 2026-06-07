package com.onesley.oneclick.core.tenant.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs des invitations tenant-admin (E2 — V78), exposés par {@code TenantController}
 * (portail SUPERADMIN, {@code VERB:TENANTS}).
 *
 * <p>Le token n'est JAMAIS exposé en lecture (il ne vit que dans le lien email). La liste
 * et la création renvoient {@link InviteDto} sans token.</p>
 */
public final class TenantAdminInviteDtos {

    private TenantAdminInviteDtos() {}

    /** {@code owner|admin|viewer} (rôle tenant-scope, cf. tenant_admins). */
    public static final String ROLE_REGEX = "^(owner|admin|viewer)$";

    /** Création d'une invitation. {@code role} optionnel (défaut {@code admin}). */
    public record CreateInviteDto(
        @Email @NotBlank @Size(min = 1, max = 256) String email,
        @Pattern(regexp = ROLE_REGEX, message = "role invalide (owner|admin|viewer)") String role
    ) {}

    /** Vue d'une invitation (liste portail admin). Pas de token. */
    public record InviteDto(
        UUID id,
        UUID tenantId,
        String email,
        String role,
        String status,
        UUID invitedBy,
        Instant expiresAt,
        Instant acceptedAt,
        Instant createdAt
    ) {}
}
