package com.onesley.oneclick.exception;

import org.springframework.http.HttpStatus;

/**
 * Payload mal formé ou paramètre invalide (400).
 *
 * <p>Pour les violations Bean Validation (ex: {@code @NotNull}), Spring lève
 * automatiquement {@code MethodArgumentNotValidException} qui est gérée
 * séparément dans {@link GlobalExceptionHandler}. {@code BadRequestException}
 * sert aux validations <i>métier</i> qu'on ne peut pas exprimer en annotations
 * (ex: incohérence date début/fin, format spécifique, etc.).
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
