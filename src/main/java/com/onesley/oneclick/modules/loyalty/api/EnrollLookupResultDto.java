package com.onesley.oneclick.modules.loyalty.api;

import java.util.UUID;

/**
 * Résultat minimal de la recherche client du flow « Inscrire membre » (staff resto).
 *
 * <p>Exposé par {@code GET /api/loyalty/enroll-member/lookup} (RBAC CREATE:LOYALTY) :
 * un staff a un besoin légitime de retrouver un client par email/téléphone pour
 * l'enrôler, mais ne possède PAS VIEW:USERS (réservé à l'admin). Ce DTO ne renvoie
 * que l'identité minimale ({@code id} + prénom/nom) — aucune autre PII — afin de
 * permettre l'étape « Client trouvé » sans ouvrir le lookup users complet.</p>
 */
public record EnrollLookupResultDto(UUID id, String firstName, String lastName) {
}
