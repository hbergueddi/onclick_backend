package com.onesley.oneclick.repository.auth;

import com.onesley.oneclick.entity.auth.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link Profile}.
 *
 * <p>Pattern pilote : queries dérivées par nom de méthode. Pas de @Query JPQL
 * pour l'instant — on garde le même style que UserRoleRepository pour faciliter
 * la génération bulk en Phase 4.
 */
@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByEmail(String email);

    Optional<Profile> findByReferralCode(String referralCode);

    Optional<Profile> findByPhone(String phone);

    /** Tous les profils d'un tenant donné (whitelabel multi-tenant). */
    List<Profile> findAllByTenantId(UUID tenantId);
}
