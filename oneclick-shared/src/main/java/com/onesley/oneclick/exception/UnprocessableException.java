package com.onesley.oneclick.exception;

import org.springframework.http.HttpStatus;

/**
 * Payload syntaxiquement valide mais qui viole une règle métier (422).
 *
 * <p>RFC 4918 — souvent utilisé pour les règles métier qu'un client devrait
 * pouvoir corriger. Distinct de {@link BadRequestException} (400) qui est plus
 * "le payload est mal formé".
 *
 * <p>Cas typiques OneClick :
 * <ul>
 *   <li>Création d'une réservation à une date passée</li>
 *   <li>Réserver plus de couverts que le restaurant n'a de capacité</li>
 *   <li>Application d'une promo expirée</li>
 *   <li>Tentative de scan d'un ticket sur un restaurant non-partenaire</li>
 * </ul>
 */
public class UnprocessableException extends ApiException {

    public UnprocessableException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
