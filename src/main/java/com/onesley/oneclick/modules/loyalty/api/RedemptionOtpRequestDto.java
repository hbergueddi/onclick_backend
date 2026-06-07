package com.onesley.oneclick.modules.loyalty.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse de {@code POST /api/loyalty/redemption-otp/request} (Gap #2).
 *
 * <p>Le code lui-même n'est JAMAIS renvoyé à l'appelant (staff) — il est livré
 * au client via une notification in-app. Le staff ne reçoit que l'id + l'expiration.
 */
public record RedemptionOtpRequestDto(
    UUID requestId,
    Instant expiresAt
) {
}
