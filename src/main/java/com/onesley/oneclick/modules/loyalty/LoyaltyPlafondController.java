package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.modules.loyalty.api.LoyaltyPlafondCreateDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyPlafondDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyPlafondPatchDto;
import com.onesley.oneclick.modules.loyalty.internal.LoyaltyPlafondService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * REST controller {@code /api/loyalty/plafonds} — plafonds / limites anti-abus
 * du programme OneClick Lounge, gérés par l'admin (FORGE).
 *
 * <p>RBAC v2 senior strict : {@code hasAuthority('VERB:LOYALTY_CAP')} uniquement
 * (migration V44, SUPERADMIN). Ressource plateforme → pas d'ABAC.
 */
@RestController
@RequestMapping("/api/loyalty/plafonds")
@Tag(name = "Loyalty Plafonds", description = "Plafonds / limites anti-abus (OneClick Lounge)")
@RequiredArgsConstructor
public class LoyaltyPlafondController {

    private final LoyaltyPlafondService service;

    @GetMapping
    @Operation(summary = "Liste les plafonds / limites Lounge")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY_CAP')")
    public List<LoyaltyPlafondDto> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crée un plafond / limite")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY_CAP')")
    public LoyaltyPlafondDto create(@Valid @RequestBody LoyaltyPlafondCreateDto dto) {
        return service.create(dto);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Met à jour un plafond (partial update)")
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY_CAP')")
    public LoyaltyPlafondDto patch(@PathVariable UUID id, @Valid @RequestBody LoyaltyPlafondPatchDto dto) {
        return service.patch(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime (soft delete) un plafond")
    @PreAuthorize("hasAuthority('DELETE:LOYALTY_CAP')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
