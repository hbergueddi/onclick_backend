package com.onesley.oneclick.core.auth.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repo des invitations d'activation de compte membre (Gap #10, V88).
 */
interface AccountActivationInviteRepository extends JpaRepository<AccountActivationInvite, UUID> {

    /** Lookup par hash de token (acceptation). */
    Optional<AccountActivationInvite> findByTokenHash(String tokenHash);

    /** Une invitation pending existe-t-elle déjà pour ce user (anti-doublon) ? */
    boolean existsByUserIdAndStatus(UUID userId, String status);
}
