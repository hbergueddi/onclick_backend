package com.onesley.oneclick.controller.tenant;

import com.onesley.oneclick.dto.tenant.TenantAdminDto;
import com.onesley.oneclick.service.tenant.TenantAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenant-admins")
@Tag(name = "Tenant Admins", description = "Junction user ↔ tenant avec rôle (owner/admin/viewer)")
@PreAuthorize("hasRole('admin')")
public class TenantAdminController {

    private final TenantAdminService service;

    public TenantAdminController(TenantAdminService service) {
        this.service = service;
    }

    @GetMapping("/by-tenant")
    @Operation(summary = "Liste les admins d'un tenant")
    public List<TenantAdminDto> findByTenant(@RequestParam UUID tenantId) {
        return service.findByTenant(tenantId);
    }

    @GetMapping("/by-user")
    @Operation(summary = "Liste les tenants où un user est admin")
    public List<TenantAdminDto> findByUser(@RequestParam UUID userId) {
        return service.findByUser(userId);
    }

    @GetMapping
    @Operation(summary = "Lookup couple (tenant,user) — admin lookup")
    public ResponseEntity<TenantAdminDto> find(
        @RequestParam UUID tenantId,
        @RequestParam UUID userId
    ) {
        return service.find(tenantId, userId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/check")
    @Operation(summary = "Vérifie qu'un user est admin d'un tenant donné")
    public boolean isAdmin(@RequestParam UUID tenantId, @RequestParam UUID userId) {
        return service.isAdmin(tenantId, userId);
    }
}
