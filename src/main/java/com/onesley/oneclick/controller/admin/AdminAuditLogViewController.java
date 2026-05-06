package com.onesley.oneclick.controller.admin;

import com.onesley.oneclick.dto.admin.AdminAuditLogViewDto;
import com.onesley.oneclick.service.admin.AdminAuditLogViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link AdminAuditLogViewDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/views/admin-audit-logs")
@Tag(name = "AdminAuditLogView", description = "Auto-generated controller for v_admin_audit_log")
public class AdminAuditLogViewController {

    private final AdminAuditLogViewService service;

    public AdminAuditLogViewController(AdminAuditLogViewService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<AdminAuditLogViewDto> findAll() {
        return service.findAll();
    }
}
