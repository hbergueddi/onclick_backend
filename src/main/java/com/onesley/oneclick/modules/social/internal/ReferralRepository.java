package com.onesley.oneclick.modules.social.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
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

    /** Liste paginée platform-wide — admin (TableauxPulse). */
    @Query("SELECT r FROM Referral r ORDER BY r.createdAt DESC")
    Page<Referral> findAllOrdered(Pageable pageable);
}
