package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.modules.loyalty.api.LoyaltyTierRuleCreateDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTierRuleDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTierRulePatchDto;
import com.onesley.oneclick.modules.loyalty.internal.LoyaltyTierRuleService;
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
 * REST controller {@code /api/loyalty/tier-rules} — paliers de fidélité PLATEFORME
 * (OneClick Lounge), gérés par l'admin (FORGE).
 *
 * <p>RBAC v2 senior strict : {@code hasAuthority('VERB:LOYALTY_TIER')} uniquement,
 * jamais {@code isAuthenticated()}/{@code hasRole()}. Ressource admin-only accordée
 * au seul SUPERADMIN (migration V43). Aucun scoping owner (ressource plateforme,
 * pas de restaurant_id/tenant_id) → pas d'ABAC.
 */
@RestController
@RequestMapping("/api/loyalty/tier-rules")
@Tag(name = "Loyalty Tier Rules", description = "Paliers de fidélité plateforme (OneClick Lounge)")
@RequiredArgsConstructor
public class LoyaltyTierRuleController {

    private final LoyaltyTierRuleService service;

    @GetMapping
    @Operation(summary = "Liste les paliers de fidélité plateforme")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY_TIER')")
    public List<LoyaltyTierRuleDto> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crée un palier de fidélité plateforme")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY_TIER')")
    public LoyaltyTierRuleDto create(@Valid @RequestBody LoyaltyTierRuleCreateDto dto) {
        return service.create(dto);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Met à jour un palier (partial update)")
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY_TIER')")
    public LoyaltyTierRuleDto patch(@PathVariable UUID id, @Valid @RequestBody LoyaltyTierRulePatchDto dto) {
        return service.patch(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime (soft delete) un palier")
    @PreAuthorize("hasAuthority('DELETE:LOYALTY_TIER')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
