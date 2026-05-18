package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload POST {@code /api/loyalty/enroll-member} — port du commit legacy e7a8b49b
 * ({@code Sprint 9 — Welcome points pour inscription membre My Homu}).
 *
 * <p>Le staff renseigne le restaurant + identité du futur membre + le bonus
 * welcome. Le service {@link com.onesley.oneclick.modules.loyalty.internal.EnrollmentService}
 * (1) trouve ou crée le user via {@code core.identity}, (2) vérifie staff
 * actif via {@code modules.restaurant}, (3) valide plafond welcome_points_max
 * via {@code loyalty.gain_rules}, (4) crédite via {@code LoyaltyService.earnPoints}.
 *
 * <p>Si {@code clientId} est fourni, on l'utilise direct (cas client existant
 * recherché via /api/users/by-email ou by-phone côté front). Sinon, le service
 * crée un nouveau user avec les champs ({@code email}, {@code firstName},
 * {@code lastName}, optional {@code phone}). Au moins un de {@code clientId}
 * OU {@code email} doit être fourni.
 *
 * <p>{@code welcomePoints} est plafonné par {@code gain_rules.welcome_points_max}.
 */
public record EnrollMemberDto(
    @NotNull UUID restaurantId,
    UUID clientId,
    @Email String email,
    String phone,
    String firstName,
    String lastName,
    @NotNull @Min(0) Integer welcomePoints,
    /** Si true, le service tente d'envoyer un email d'invitation (TODO V2 SMTP). */
    Boolean sendInvite
) {
}
