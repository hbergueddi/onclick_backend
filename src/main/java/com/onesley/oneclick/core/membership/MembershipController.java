package com.onesley.oneclick.core.membership;

import com.onesley.oneclick.core.membership.api.InviteMemberDto;
import com.onesley.oneclick.core.membership.api.MembershipDto;
import com.onesley.oneclick.core.membership.api.MembershipKpiDto;
import com.onesley.oneclick.core.membership.api.TenantMemberDto;
import com.onesley.oneclick.core.membership.internal.MembershipInviteService;
import com.onesley.oneclick.core.membership.internal.MembershipQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints d'administration des membres d'un programme (P2 — « l'admin du tenant invite »).
 *
 * <p>Sous {@code /api/tenants/{tenantId}/...} (l'invitation est faite par l'admin DU tenant, depuis
 * {@code tenant/{slug}}). Le contrôleur vit dans le module {@code core.membership} qui <b>owne</b> le
 * concept de membership ; il délègue au service interne (frontière Modulith respectée).</p>
 *
 * <p>RBAC strict : {@code hasAuthority('CREATE:MEMBERSHIPS')} (seed V94 → RESTAURATEUR/GROUP_ADMIN/
 * SUPERADMIN). L'ABAC own-tenant (un admin n'invite que dans SON tenant) est appliqué dans le
 * service.</p>
 */
@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Memberships", description = "Membres d'un programme (invitation par l'admin du tenant)")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipInviteService inviteService;
    private final MembershipQueryService queryService;

    @GetMapping("/{tenantId}/members")
    @Operation(
        summary = "Liste les membres actifs du programme du tenant (page admin Membres)",
        description = "RBAC : VIEW:MEMBERSHIPS ; ABAC : l'admin ne voit que SON tenant (SUPERADMIN : "
            + "tout tenant). Identité enrichie (nom/email) via le contrat identity."
    )
    @PreAuthorize("hasAuthority('VIEW:MEMBERSHIPS')")
    public List<TenantMemberDto> listMembers(@PathVariable UUID tenantId) {
        return queryService.listMembers(tenantId);
    }

    @GetMapping("/{tenantId}/members/kpis")
    @Operation(
        summary = "KPIs membres du tenant (snapshot REST ; mises à jour live via STOMP /topic/admin/membership-kpis)",
        description = "RBAC : VIEW:MEMBERSHIPS ; ABAC own-tenant. Le front affiche ce snapshot et le "
            + "rafraîchit sur push WebSocket (pas de polling)."
    )
    @PreAuthorize("hasAuthority('VIEW:MEMBERSHIPS')")
    public MembershipKpiDto memberKpis(@PathVariable UUID tenantId) {
        return queryService.kpisForTenant(tenantId);
    }

    @PostMapping("/{tenantId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Invite une personne dans le programme du tenant (compte OneClick unique + membership)",
        description = "Réutilise le compte OneClick existant (email/téléphone) sinon le crée (home "
            + "oneclick), puis ajoute une membership active (rôle MEMBER). RBAC : CREATE:MEMBERSHIPS ; "
            + "ABAC : l'admin n'invite que dans son propre tenant (SUPERADMIN : tout tenant)."
    )
    @PreAuthorize("hasAuthority('CREATE:MEMBERSHIPS')")
    public MembershipDto invite(@PathVariable UUID tenantId, @Valid @RequestBody InviteMemberDto body) {
        return inviteService.invite(tenantId, body);
    }
}
