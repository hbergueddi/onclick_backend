package com.onesley.oneclick.core.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "Multi-tenant — racine whitelabel (OneClick, HOMU, PCC, ...)")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les tenants actifs")
    public List<TenantDto> findAll() { return service.findAll(); }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un tenant par UUID")
    public TenantDto findById(@PathVariable UUID id) { return service.findById(id); }

    @GetMapping("/by-slug")
    @Operation(summary = "Lookup tenant par slug (whitelabel routing)")
    public TenantDto findBySlug(@RequestParam String slug) { return service.findBySlug(slug); }

    @PostMapping
    @Operation(summary = "Crée un tenant")
    public ResponseEntity<TenantDto> create(@Valid @RequestBody TenantCreateDto dto) {
        TenantDto t = service.create(dto);
        return ResponseEntity.created(URI.create("/api/tenants/" + t.id())).body(t);
    }
}
