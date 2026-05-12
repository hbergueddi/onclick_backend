/**
 * Module {@code core/auth} — flow d'authentification (refresh tokens, login history, OTP).
 *
 * <h3>Tables</h3>
 * <ul>
 *   <li>{@code refresh_tokens} — rotation JWT, révocation possible</li>
 *   <li>{@code login_histories} — historique connexions (anti brute-force, audit)</li>
 *   <li>{@code otp_requests} — codes OTP (signup, reset, 2FA, redemption)</li>
 * </ul>
 *
 * <h3>Dépendance</h3>
 * <p>Référence {@link com.onesley.oneclick.core.identity.api.User} pour les FKs {@code user_id}.
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "core.auth",
    displayName = "core/auth",
    allowedDependencies = {"core.identity", "exception", "security"}
)
package com.onesley.oneclick.core.auth;

import org.springframework.modulith.ApplicationModule;
