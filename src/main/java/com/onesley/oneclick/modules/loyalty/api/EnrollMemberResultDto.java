package com.onesley.oneclick.modules.loyalty.api;

import java.util.UUID;

/**
 * Résultat POST {@code /api/loyalty/enroll-member} (cf. {@link EnrollMemberDto}).
 *
 * @param clientId      ID du membre (existant ou nouvellement créé)
 * @param isNewUser     {@code true} si user créé pendant l'enrollment
 * @param pointsGranted points effectivement crédités (peut être &lt; demande si plafonné)
 * @param accountId     ID du loyalty_account créé/réutilisé
 * @param transactionId ID de la transaction earn créée
 * @param inviteSent    {@code true} si email d'invitation envoyé (false sinon)
 */
public record EnrollMemberResultDto(
    UUID clientId,
    boolean isNewUser,
    int pointsGranted,
    UUID accountId,
    UUID transactionId,
    boolean inviteSent
) {
}
