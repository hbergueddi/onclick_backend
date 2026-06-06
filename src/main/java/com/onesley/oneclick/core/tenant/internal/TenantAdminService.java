package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.AddTenantAdminDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantAdminDto;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administrateurs de tenant (portail tenant-admin C2, SUPERADMIN-only).
 *
 * <p>Port du legacy {@code tenant_admins} (gestion via TenantAdminsDialog). list / add / remove,
 * réservés SUPERADMIN (garde {@code hasAuthority('VERB:TENANTS')} sur le controller). Les noms /
 * contacts des admins sont résolus à la lecture via {@code UserDirectoryApi} (core.identity OPEN) —
 * aucune dénormalisation ni lecture SQL native de {@code users}.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class TenantAdminService {

    private static final Set<String> VALID_ROLES = Set.of("owner", "admin", "viewer");

    private final TenantRepository tenantRepository;
    private final TenantAdminRepository repo;
    private final UserDirectoryApi userDirectory;

    /** Admins d'un tenant (enrichis nom/contact), du plus récent au plus ancien. */
    public List<TenantAdminDto> listAdmins(UUID tenantId) {
        requireTenant(tenantId);
        List<TenantAdmin> rows = repo.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserName> names = userDirectory
            .namesByIds(rows.stream().map(TenantAdmin::getUserId).toList())
            .stream()
            .collect(Collectors.toMap(UserName::id, Function.identity(), (a, b) -> a));
        return rows.stream().map(r -> toDto(r, names.get(r.getUserId()))).toList();
    }

    /**
     * Ajoute un admin par identifiant humain (résolu dans le tenant via UserDirectoryApi).
     * 404 si aucun membre du tenant ne correspond ; 409 si déjà admin. Rôle défaut {@code admin}.
     */
    @Transactional
    public TenantAdminDto addAdmin(UUID tenantId, AddTenantAdminDto dto) {
        requireTenant(tenantId);
        String identifier = dto.identifier() == null ? null : dto.identifier().trim();
        if (identifier == null || identifier.isEmpty()) {
            throw new BadRequestException("Identifiant requis");
        }
        String role = dto.role() == null || dto.role().isBlank() ? "admin" : dto.role().trim();
        if (!VALID_ROLES.contains(role)) {
            throw new BadRequestException("Rôle invalide (owner|admin|viewer)");
        }

        UserName user = userDirectory.findByIdentifier(identifier, tenantId)
            .orElseThrow(() -> new NotFoundException(
                "Aucun membre de ce tenant pour l'identifiant : " + identifier));

        if (repo.existsByTenantIdAndUserId(tenantId, user.id())) {
            throw new ConflictException("Cet utilisateur est déjà administrateur de ce tenant.");
        }

        UUID caller = SecurityHelper.currentUserId();
        TenantAdmin saved = repo.save(new TenantAdmin(UUID.randomUUID(), tenantId, user.id(), role, caller));
        log.info("[tenant-admin] add (tenant={}, user={}, role={}, by={})", tenantId, user.id(), role, caller);
        return toDto(saved, user);
    }

    /** Retire un admin du tenant. 404 si l'utilisateur n'est pas admin de ce tenant. */
    @Transactional
    public void removeAdmin(UUID tenantId, UUID userId) {
        requireTenant(tenantId);
        TenantAdmin row = repo.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new NotFoundException("TenantAdmin", userId));
        repo.delete(row);
        log.info("[tenant-admin] remove (tenant={}, user={})", tenantId, userId);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void requireTenant(UUID tenantId) {
        tenantRepository.findById(tenantId)
            .filter(t -> t.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Tenant", tenantId));
    }

    private static TenantAdminDto toDto(TenantAdmin a, UserName u) {
        return new TenantAdminDto(
            a.getUserId(),
            u != null ? u.firstName() : null,
            u != null ? u.lastName() : null,
            u != null ? u.email() : null,
            a.getRole(),
            a.getInvitedBy(),
            a.getCreatedAt());
    }
}
