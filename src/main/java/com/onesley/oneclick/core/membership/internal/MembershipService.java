package com.onesley.oneclick.core.membership.internal;

import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import com.onesley.oneclick.core.tenant.api.TenantDirectoryApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Implémentation du port {@link MembershipDirectoryApi} (lecture des appartenances).
 *
 * <p>P0/P1 : lectures seules sur {@code tenant_memberships}. Consommé par {@code security}
 * ({@link #authoritiesFor(UUID)} — pliage des authorities) et, à terme, par les modules business
 * pour l'ABAC. « Actif » = {@code status = 'active'} ET {@code deleted_at IS NULL} (garanti par les finders).
 */
@Service
public class MembershipService implements MembershipDirectoryApi {

    private final MembershipRepository repository;
    private final TenantDirectoryApi tenantDirectory;
    /** Slug du tenant public « oneclick » (baseline de visibilité) — surchargeable par env. */
    private final String publicTenantSlug;

    public MembershipService(MembershipRepository repository,
                             TenantDirectoryApi tenantDirectory,
                             @Value("${app.tenant.public-slug:oneclick}") String publicTenantSlug) {
        this.repository = repository;
        this.tenantDirectory = tenantDirectory;
        this.publicTenantSlug = publicTenantSlug;
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
    public List<MembershipView> activeMembershipViews(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return repository.findActiveMembershipViews(userId).stream()
                .map(r -> new MembershipView(
                        (UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4]))
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

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> visibleTenantIds(UUID userId) {
        // Baseline = tenant public « oneclick » (toujours visible, y compris non authentifié).
        Set<UUID> visible = new HashSet<>();
        tenantDirectory.findIdBySlug(publicTenantSlug).ifPresent(visible::add);
        // + programmes dont le caller est membre actif (PCC, HOMU, …).
        if (userId != null) {
            visible.addAll(activeTenantIds(userId));
        }
        return visible;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> authoritiesFor(UUID userId) {
        if (userId == null) {
            return Set.of();
        }
        return new HashSet<>(repository.findActiveMembershipAuthorities(userId));
    }
}
