package com.onesley.oneclick.modules.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link Referral} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface ReferralRepository extends JpaRepository<Referral, UUID>, JpaSpecificationExecutor<Referral> {
    java.util.List<Referral> findAllByReferrerId(java.util.UUID referrerId);
    java.util.List<Referral> findAllByReferredUserId(java.util.UUID referredUserId);
}
