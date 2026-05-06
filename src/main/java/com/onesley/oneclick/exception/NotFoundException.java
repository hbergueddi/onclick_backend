package com.onesley.oneclick.exception;

import org.springframework.http.HttpStatus;

/**
 * Ressource introuvable (404).
 *
 * <p>Convention de message : {@code "<EntityName> not found: <identifier>"}
 * pour faciliter le debugging côté client.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String entityName, Object id) {
        super(HttpStatus.NOT_FOUND, entityName + " not found: " + id);
    }

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
