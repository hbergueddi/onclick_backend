package com.onesley.oneclick.exception;

import org.springframework.http.HttpStatus;

/**
 * Pas authentifié (401).
 *
 * <p>Spring Security émet ses propres {@code AuthenticationException} qui sont
 * mappées sur 401 directement par {@link GlobalExceptionHandler}.
 * {@code UnauthorizedException} sert quand le service métier détecte un cas
 * d'auth manquante hors du flow Spring Security (ex: dans un cron / un test).
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
