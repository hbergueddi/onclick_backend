package com.onesley.oneclick.core.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link TenantAdminInvite} — lookup par token hashé + liste par tenant.
 */
@Repository
public interface TenantAdminInviteRepository extends JpaRepository<TenantAdminInvite, UUID> {

    /** Lookup par SHA-256 du token (acceptation). */
    Optional<TenantAdminInvite> findByTokenHash(String tokenHash);

    /** Invitations d'un tenant, de la plus récente à la plus ancienne (portail admin). */
    List<TenantAdminInvite> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
