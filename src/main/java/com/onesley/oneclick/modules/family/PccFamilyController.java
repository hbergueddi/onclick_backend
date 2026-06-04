package com.onesley.oneclick.modules.family;

import com.onesley.oneclick.modules.family.api.PccFamilyDtos.AddFamilyMemberDto;
import com.onesley.oneclick.modules.family.api.PccFamilyDtos.FamilyMemberDto;
import com.onesley.oneclick.modules.family.api.PccFamilyDtos.PointsHistoryEntryDto;
import com.onesley.oneclick.modules.family.internal.PccFamilyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * REST controller {@code /api/pcc/family} — « Ma Famille » (PCC Lot 5).
 *
 * <p>RBAC v2 senior strict — {@code @PreAuthorize("hasAuthority('VERB:FAMILY')")} uniquement
 * (jamais isAuthenticated/hasRole). Le scoping fin (caller = A, scope tenant du caller,
 * relation requise pour l'historique, retrait par A ou B) est porté par {@link PccFamilyService}
 * (ABAC). Ressource {@code FAMILY} seedée par V68.
 * <ul>
 *   <li>{@code GET /} — VIEW:FAMILY — ma liste famille + total points restants de chacun.</li>
 *   <li>{@code POST /} — CREATE:FAMILY — ajoute un proche (email / code OC- / téléphone).</li>
 *   <li>{@code GET /{targetId}/points-history} — VIEW:FAMILY — historique points d'un proche
 *       (403 si pas dans ma famille).</li>
 *   <li>{@code DELETE /{relationId}} — DELETE:FAMILY — retire un lien (caller = A ou B).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pcc/family")
@Tag(name = "PccFamily", description = "Ma Famille (PCC Lot 5) — proches d'un membre + points fidélité")
@RequiredArgsConstructor
public class PccFamilyController {

    private final PccFamilyService service;

    @GetMapping
    @Operation(summary = "Ma liste famille (self-scope) + total points restants de chaque proche")
    @PreAuthorize("hasAuthority('VIEW:FAMILY')")
    public List<FamilyMemberDto> myFamily() {
        return service.listMyFamily();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un proche par email / code parrainage OC- / téléphone")
    @PreAuthorize("hasAuthority('CREATE:FAMILY')")
    public FamilyMemberDto add(@Valid @RequestBody AddFamilyMemberDto body) {
        return service.addFamilyMember(body);
    }

    @GetMapping("/{targetId}/points-history")
    @Operation(summary = "Historique de points fidélité d'un proche (403 s'il n'est pas dans ma famille)")
    @PreAuthorize("hasAuthority('VIEW:FAMILY')")
    public List<PointsHistoryEntryDto> pointsHistory(@PathVariable UUID targetId) {
        return service.memberPointsHistory(targetId);
    }

    @DeleteMapping("/{relationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retirer un lien famille (le caller doit être A ou B)")
    @PreAuthorize("hasAuthority('DELETE:FAMILY')")
    public void remove(@PathVariable UUID relationId) {
        service.removeFamilyMember(relationId);
    }
}
