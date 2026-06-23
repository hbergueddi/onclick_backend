package com.onesley.oneclick.exception;

import org.springframework.http.HttpStatus;

/**
 * 403 — login refusé car l'adresse email du compte n'a pas encore été vérifiée
 * (P1 enrollment, statut {@code pending_email_verification}). Le mot de passe est
 * correct, mais le compte reste gaté tant que l'OTP envoyé par email n'a pas été
 * validé via {@code POST /api/auth/otp/verify}.
 *
 * <p>Contrat client (RFC 7807) : le {@code ProblemDetail.type} se termine par
 * {@code email-not-verified} (slug dérivé du nom de classe par
 * {@link GlobalExceptionHandler}). Le client (iOS/Android/web) détecte ce slug
 * après un login 403 et route l'utilisateur vers l'écran de saisie du code OTP
 * (il connaît déjà l'email saisi au login).</p>
 *
 * <p>Distinct de {@link ForbiddenException} (autorisation RBAC) : même code HTTP 403
 * mais slug différent ({@code email-not-verified} vs {@code forbidden}) → discriminant.</p>
 */
public class EmailNotVerifiedException extends ApiException {

    public EmailNotVerifiedException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
