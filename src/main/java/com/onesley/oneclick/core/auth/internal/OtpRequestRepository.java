package com.onesley.oneclick.core.auth.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link OtpRequest} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface OtpRequestRepository extends JpaRepository<OtpRequest, UUID>, JpaSpecificationExecutor<OtpRequest> {
    java.util.List<OtpRequest> findAllByUserId(java.util.UUID userId);

    /**
     * Dernier OTP non encore vérifié pour un user et un purpose donné (le plus récent).
     * Utilisé par le flow « mot de passe oublié » (reset_password) où le client ne connaît
     * pas l'{@code otpId} (anti-énumération : {@code forgot-password} ne renvoie pas d'id) :
     * la vérification se fait par (email → user) + purpose + code.
     */
    java.util.Optional<OtpRequest> findTopByUserIdAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
        java.util.UUID userId, String purpose);
}
