package com.onesley.oneclick.modules.restaurant.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTOs pour les workflows staff — Sprint G.5 (port EFs Supabase).
 *
 * <ul>
 *   <li>{@code TransferDto}    : POST /api/restaurants/staff/transfer
 *       Transfère un staff d'un resto vers un autre (workflow ProDesk).</li>
 *   <li>{@code InviteDto}      : POST /api/restaurants/staff/invite
 *       Invite un user (par email ou phone) à rejoindre un resto comme staff.</li>
 * </ul>
 */
public final class StaffTransferDto {

    private StaffTransferDto() {}

    /** Transfert staff entre 2 restaurants (port EF transfer-staff). */
    public record TransferDto(
        @NotNull UUID staffId,
        @NotNull UUID sourceRestaurantId,
        @NotNull UUID targetRestaurantId,
        @Size(min = 1, max = 1024) String reason
    ) {}

    /** Invitation team member par email/phone (port EF invite-team-member). */
    public record InviteDto(
        @NotNull UUID restaurantId,
        @Size(min = 1, max = 256) String email,
        @Size(min = 1, max = 64) String phone,
        @NotNull @Size(min = 1, max = 64) String roleCode,
        @Size(min = 1, max = 1024) String invitationMessage
    ) {}

    /** Résultat invitation : si user existe → ajouté direct, sinon notif/email envoyée. */
    public record InviteResultDto(
        boolean userExists,
        UUID staffId,
        String message
    ) {}
}
