package com.onesley.oneclick.exception;

import org.springframework.http.HttpStatus;

/**
 * État incompatible avec l'opération demandée (409).
 *
 * <p>Cas typiques :
 * <ul>
 *   <li>Tentative d'INSERT qui violerait un UNIQUE constraint</li>
 *   <li>Tentative de modifier une réservation dans un statut final (honorée, annulée)</li>
 *   <li>Conflit de concurrence (optimistic locking)</li>
 * </ul>
 *
 * <p>Les {@code DataIntegrityViolationException} de Spring Data sont également
 * mappées vers 409 quand elles correspondent à un UNIQUE constraint.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
