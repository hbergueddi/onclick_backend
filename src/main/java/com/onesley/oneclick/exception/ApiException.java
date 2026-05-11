package com.onesley.oneclick.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception métier de base — capture le code HTTP cible + un message lisible.
 *
 * <p>Toutes les exceptions applicatives héritent de celle-ci pour permettre au
 * {@link GlobalExceptionHandler} de les mapper en {@code ProblemDetails} RFC 7807
 * sans avoir besoin d'un {@code @ExceptionHandler} par sous-type.
 *
 * <p>Convention :
 * <ul>
 *   <li>{@link NotFoundException}     → 404</li>
 *   <li>{@link BadRequestException}   → 400 (validation, payload mal formé)</li>
 *   <li>{@link UnauthorizedException} → 401 (pas authentifié)</li>
 *   <li>{@link ForbiddenException}    → 403 (authentifié mais pas autorisé)</li>
 *   <li>{@link ConflictException}     → 409 (UNIQUE constraint, état incompatible)</li>
 *   <li>{@link UnprocessableException} → 422 (règle métier violée)</li>
 * </ul>
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
