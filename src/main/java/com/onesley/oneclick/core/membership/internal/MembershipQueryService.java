package com.onesley.oneclick.core.membership.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.membership.api.MembershipKpiDto;
import com.onesley.oneclick.core.membership.api.TenantMemberDto;
import com.onesley.oneclick.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lectures admin du module membership (P3) : liste des membres d'un programme + KPIs.
 *
 * <p>{@link #listMembers(UUID)} : page « Membres » de l'admin tenant — ABAC own-tenant
 * (un admin ne voit que SON tenant ; bypass SUPERADMIN via {@code DELETE:TENANTS}). Identité
 * enrichie via le contrat {@code UserDirectoryApi} (pas de JOIN users cross-module).</p>
 *
 * <p>{@link #computeKpis(UUID)} : agrégats pour le dashboard temps réel, consommé par
 * {@code MembershipKpiPublisher} (WebSocket/STOMP) — donc <b>sans</b> contrôle de contexte requête
 * (le publisher n'est pas dans un appel HTTP ; l'autorisation d'abonnement suit le pattern existant
 * {@code TenantKpisPublisher}).</p>
 */
@Service
@RequiredArgsConstructor
public class MembershipQueryService {

    /** Autorité EXCLUSIVE SUPERADMIN (vérifiée en DB) → bypass de l'ABAC own-tenant. */
    private static final String PLATFORM_ADMIN_AUTHORITY = "DELETE:TENANTS";

    private final MembershipRepository repository;
    private final UserDirectoryApi userDirectory;

    @Transactional(readOnly = true)
    public List<TenantMemberDto> listMembers(UUID tenantId) {
        requireTenantAdmin(tenantId);
        List<TenantMembership> members =
            repository.findAllByTenantIdAndStatusAndDeletedAtIsNull(tenantId, TenantMembership.STATUS_ACTIVE);
        List<UUID> ids = members.stream().map(TenantMembership::getUserId).distinct().toList();
        Map<UUID, UserDirectoryApi.UserName> names = userDirectory.namesByIds(ids).stream()
            .collect(Collectors.toMap(UserDirectoryApi.UserName::id, n -> n, (a, b) -> a));
        return members.stream().map(m -> {
            UserDirectoryApi.UserName n = names.get(m.getUserId());
            return new TenantMemberDto(
                m.getUserId(),
                n != null ? n.firstName() : null,
                n != null ? n.lastName() : null,
                n != null ? n.email() : null,
                n != null ? n.phone() : null,
                m.getMemberType(), m.getStatus(), m.getJoinedAt());
        }).toList();
    }

    /** KPIs du tenant pour la page admin (snapshot REST) — ABAC own-tenant (le publisher STOMP, lui,
     *  appelle directement {@link #computeKpis} hors contexte requête). */
    @Transactional(readOnly = true)
    public MembershipKpiDto kpisForTenant(UUID tenantId) {
        requireTenantAdmin(tenantId);
        return computeKpis(tenantId);
    }

    @Transactional(readOnly = true)
    public MembershipKpiDto computeKpis(UUID tenantId) {
        long total = repository.countActiveByTenant(tenantId);
        long newLast7 = repository.countActiveByTenantSince(tenantId, Instant.now().minus(7, ChronoUnit.DAYS));
        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] r : repository.countActiveByTenantGroupedByType(tenantId)) {
            String type = (String) r[0];
            long count = ((Number) r[1]).longValue();
            byType.put(type == null || type.isBlank() ? "(non précisé)" : type, count);
        }
        return new MembershipKpiDto(tenantId, total, newLast7, byType);
    }

    private void requireTenantAdmin(UUID tenantId) {
        UUID caller = MembershipSecurityContext.currentUserId();
        if (caller == null) {
            throw new ForbiddenException("Non authentifié");
        }
        if (!MembershipSecurityContext.hasAuthority(PLATFORM_ADMIN_AUTHORITY)) {
            UUID home = userDirectory.tenantIdById(caller).orElse(null);
            if (!tenantId.equals(home)) {
                throw new ForbiddenException(
                    "Accès refusé : vous ne pouvez consulter que les membres de votre propre tenant");
            }
        }
    }
}
