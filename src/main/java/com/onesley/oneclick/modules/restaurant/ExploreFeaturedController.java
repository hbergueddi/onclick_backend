package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.modules.restaurant.api.ExploreFeaturedDtos.*;
import com.onesley.oneclick.modules.restaurant.internal.ExploreFeaturedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/restaurants/featured")
@Tag(name = "Explore featured", description = "Sprint H — curation manuelle restaurants featured Explore")
public class ExploreFeaturedController {

    private final ExploreFeaturedService service;

    public ExploreFeaturedController(ExploreFeaturedService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste publique des restos featured (Explore)")
    public List<ExploreFeaturedDto> findAllEnabled() {
        return service.findAllEnabled();
    }

    // Bug 32 (Batch D RBAC v2) — RESOURCE=RESTAURANTS (featured = curation resto).
    @PostMapping
    @Operation(summary = "Upsert featured (admin)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN') or hasAuthority('UPDATE:RESTAURANTS')")
    public ResponseEntity<ExploreFeaturedDto> upsert(@Valid @RequestBody ExploreFeaturedCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upsert(dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN') or hasAuthority('DELETE:RESTAURANTS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
