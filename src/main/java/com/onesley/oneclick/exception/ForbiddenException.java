package com.onesley.oneclick.exception;

import org.springframework.http.HttpStatus;

/**
 * Authentifié mais pas autorisé sur cette ressource (403).
 *
 * <p>Distinct de {@link UnauthorizedException} (401) — le user EST connu mais
 * son rôle / sa relation à la ressource ne lui donne pas l'accès.
 *
 * <p>Spring Security {@code AccessDeniedException} est mappée vers 403
 * automatiquement par {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
