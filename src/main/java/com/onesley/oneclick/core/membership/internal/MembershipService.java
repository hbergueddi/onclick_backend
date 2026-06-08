package com.onesley.oneclick.core.membership.internal;

import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implémentation du port {@link MembershipDirectoryApi} (lecture des appartenances).
 *
 * <p>P0 : lectures seules sur {@code tenant_memberships}. Rien d'autre ne consomme encore ce
 * service (P1 : {@code security} pour le pliage des authorities + modules business pour l'ABAC).
 * « Actif » = {@code status = 'active'} ET {@code deleted_at IS NULL} (garanti par les finders).
 */
@Service
public class MembershipService implements MembershipDirectoryApi {

    private final MembershipRepository repository;

    public MembershipService(MembershipRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID userId, UUID tenantId) {
        if (userId == null || tenantId == null) {
            return false;
        }
        return repository.existsByUserIdAndTenantIdAndStatusAndDeletedAtIsNull(
                userId, tenantId, TenantMembership.STATUS_ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Membership> activeMemberships(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return repository.findAllByUserIdAndStatusAndDeletedAtIsNull(userId, TenantMembership.STATUS_ACTIVE)
                .stream()
                .map(m -> new Membership(m.getTenantId(), m.getMemberType(), m.getStatus()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeTenantIds(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return repository.findAllByUserIdAndStatusAndDeletedAtIsNull(userId, TenantMembership.STATUS_ACTIVE)
                .stream()
                .map(TenantMembership::getTenantId)
                .toList();
    }
}
