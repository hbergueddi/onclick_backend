package com.onesley.oneclick.exception;

import org.springframework.http.HttpStatus;

/**
 * Quota / rate-limit applicatif dépassé (429 Too Many Requests).
 *
 * <p>Distinct du rate-limiting transport (Bucket4j sur les Edge endpoints) : ici
 * c'est une <b>règle métier de quota</b> exprimée en domaine — ex. nombre maximal
 * d'imports de contacts par jour et par utilisateur.
 *
 * <p>Mappée automatiquement vers 429 par {@link GlobalExceptionHandler} via
 * {@link ApiException#getStatus()} (pas de {@code @ExceptionHandler} dédié requis).
 *
 * <p>Cas typiques OneClick :
 * <ul>
 *   <li>Import du carnet d'adresses au-delà de 10 fois / 24 h (module social)</li>
 * </ul>
 */
public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
