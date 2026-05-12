package com.onesley.oneclick.core.tenant;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.core.tenant.internal.TenantService;
import com.onesley.oneclick.search.Searchable;
import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.core.tenant.api.TenantCreateDto;
import com.onesley.oneclick.core.tenant.api.TenantDto;
import com.onesley.oneclick.core.tenant.internal.TenantRepository;

@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "Multi-tenant — racine whitelabel (OneClick, HOMU, PCC, ...)")
public class TenantController {

    /** Whitelist Phase 4 §6.3 — champs filtrables/sortables. */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "name", "slug", "status", "createdAt", "updatedAt"
    );

    private final TenantService service;
    private final TenantRepository tenantRepository;

    public TenantController(TenantService service, TenantRepository tenantRepository) {
        this.service = service;
        this.tenantRepository = tenantRepository;
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

    @PostMapping("/search")
    @Operation(summary = "Recherche dynamique (Phase 4 §6.3) — 12 opérateurs + whitelist")
    public PageResponse<TenantDto> search(@RequestBody SearchRequest req) {
        return PageResponse.from(
            Searchable.execute(tenantRepository, req, SEARCHABLE_FIELDS, TenantDto::from)
        );
    }
}
