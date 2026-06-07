package com.onesley.oneclick.core.tenant;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.core.tenant.internal.TenantService;
import com.onesley.oneclick.search.Searchable;
import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.core.tenant.api.TenantCreateDto;
import com.onesley.oneclick.core.tenant.api.TenantDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantBrandingDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantBrandingUpdateDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantFeatureDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantFeatureToggleDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantUpdateDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantAdminDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.AddTenantAdminDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminInviteDtos.CreateInviteDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminInviteDtos.InviteDto;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.tenant.internal.TenantRepository;
import com.onesley.oneclick.core.tenant.internal.TenantAdminService;
import com.onesley.oneclick.core.tenant.internal.TenantAdminInviteService;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "Multi-tenant — racine whitelabel (OneClick, HOMU, PCC, ...)")
@RequiredArgsConstructor
public class TenantController {

    /** Whitelist Phase 4 §6.3 — champs filtrables/sortables. */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "name", "slug", "status", "createdAt", "updatedAt"
    );

    private final TenantService service;
    private final TenantAdminService adminService;
    private final TenantAdminInviteService inviteService;
    private final TenantRepository tenantRepository;

    // Bug 32 (Batch B RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:TENANTS')
    @GetMapping
    @Operation(summary = "Liste tous les tenants actifs (SUPERADMIN only)")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public List<TenantDto> findAll() { return service.findAll(); }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un tenant par UUID")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public TenantDto findById(@PathVariable UUID id) { return service.findById(id); }

    @GetMapping("/by-slug")
    @Operation(summary = "Lookup tenant par slug (whitelabel routing — PUBLIC)")
    public TenantDto findBySlug(@RequestParam String slug) { return service.findBySlug(slug); }

    @PostMapping
    @Operation(summary = "Crée un tenant (SUPERADMIN only)")
    @PreAuthorize("hasAuthority('CREATE:TENANTS')")
    public ResponseEntity<TenantDto> create(@Valid @RequestBody TenantCreateDto dto) {
        TenantDto t = service.create(dto);
        return ResponseEntity.created(URI.create("/api/tenants/" + t.id())).body(t);
    }

    @PostMapping("/search")
    @Operation(summary = "Recherche dynamique (SUPERADMIN only)")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public PageResponse<TenantDto> search(@RequestBody SearchRequest req) {
        return PageResponse.from(
            Searchable.execute(tenantRepository, req, SEARCHABLE_FIELDS, Tenant::toDto)
        );
    }

    // ─── C1 portail tenant-admin (SUPERADMIN-only — hasAuthority('VERB:TENANTS')) ──
    // Décision projet : SUPERADMIN-only, pas d'ABAC self-scope ni d'impersonation. UPDATE:TENANTS
    // déjà accordé à SUPERADMIN (V32 — 6 actions × toutes les feuilles).

    @PatchMapping("/{id}")
    @Operation(summary = "Met à jour un tenant (nom/statut ; SUPERADMIN only)")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public TenantDto update(@PathVariable UUID id, @Valid @RequestBody TenantUpdateDto body) {
        return service.updateTenant(id, body);
    }

    @GetMapping("/{id}/branding")
    @Operation(summary = "Branding visuel d'un tenant")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public TenantBrandingDto getBranding(@PathVariable UUID id) {
        return service.getBranding(id);
    }

    @PutMapping("/{id}/branding")
    @Operation(summary = "Remplace le branding d'un tenant (SUPERADMIN only)")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public TenantBrandingDto updateBranding(@PathVariable UUID id, @Valid @RequestBody TenantBrandingUpdateDto body) {
        return service.updateBranding(id, body);
    }

    @GetMapping("/{id}/features")
    @Operation(summary = "Feature flags d'un tenant")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public List<TenantFeatureDto> features(@PathVariable UUID id) {
        return service.listFeatures(id);
    }

    @PutMapping("/{id}/features/{code}")
    @Operation(summary = "Active/désactive un feature flag (SUPERADMIN only)")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public TenantFeatureDto toggleFeature(@PathVariable UUID id, @PathVariable String code,
                                          @Valid @RequestBody TenantFeatureToggleDto body) {
        return service.toggleFeature(id, code, body.enabled());
    }

    // ─── C2 administrateurs de tenant (SUPERADMIN-only) ───────────────────────────

    @GetMapping("/{id}/admins")
    @Operation(summary = "Administrateurs d'un tenant (enrichis nom/contact)")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public List<TenantAdminDto> admins(@PathVariable UUID id) {
        return adminService.listAdmins(id);
    }

    @PostMapping("/{id}/admins")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute un administrateur au tenant (par identifiant ; SUPERADMIN only)")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public TenantAdminDto addAdmin(@PathVariable UUID id, @Valid @RequestBody AddTenantAdminDto body) {
        return adminService.addAdmin(id, body);
    }

    @DeleteMapping("/{id}/admins/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retire un administrateur du tenant (SUPERADMIN only)")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public void removeAdmin(@PathVariable UUID id, @PathVariable UUID userId) {
        adminService.removeAdmin(id, userId);
    }

    // ─── E2 invitations tenant-admin par email (SUPERADMIN-only) ──────────────────
    // L'acceptation (POST /api/auth/accept-tenant-admin-invite) est PUBLIQUE et vit
    // dans core/auth (création de compte + JWT) — gardée par le token, pas une authority.

    @GetMapping("/{id}/admin-invites")
    @Operation(summary = "Invitations tenant-admin d'un tenant (sans token)")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public List<InviteDto> adminInvites(@PathVariable UUID id) {
        return inviteService.listInvites(id);
    }

    @PostMapping("/{id}/admin-invites")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite un administrateur de tenant par email (lien magique ; SUPERADMIN only)")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public InviteDto createAdminInvite(@PathVariable UUID id, @Valid @RequestBody CreateInviteDto body) {
        return inviteService.createInvite(id, body);
    }

    @DeleteMapping("/{id}/admin-invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Révoque une invitation tenant-admin en attente (SUPERADMIN only)")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public void revokeAdminInvite(@PathVariable UUID id, @PathVariable UUID inviteId) {
        inviteService.revokeInvite(id, inviteId);
    }
}
