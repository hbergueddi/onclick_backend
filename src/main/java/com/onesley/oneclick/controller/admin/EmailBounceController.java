package com.onesley.oneclick.controller.admin;

import com.onesley.oneclick.dto.admin.EmailBounceDto;
import com.onesley.oneclick.service.admin.EmailBounceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link EmailBounceDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/email-bounces")
@Tag(name = "EmailBounce", description = "Auto-generated controller for email_bounces")
public class EmailBounceController {

    private final EmailBounceService service;

    public EmailBounceController(EmailBounceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<EmailBounceDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une EmailBounce par UUID")
    public ResponseEntity<EmailBounceDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
