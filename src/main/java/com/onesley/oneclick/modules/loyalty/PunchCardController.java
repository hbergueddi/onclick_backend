package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.modules.loyalty.api.PunchCardAdminDto;
import com.onesley.oneclick.modules.loyalty.api.PunchCardDto;
import com.onesley.oneclick.modules.loyalty.internal.PunchCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * REST controller {@code /api/punch-cards} — cartes de fidélité « punch cards » 10/1 (PCC Lot 3).
 *
 * <p>RBAC v2 senior strict — {@code @PreAuthorize("hasAuthority('VERB:PUNCH_CARDS')")} uniquement
 * (jamais isAuthenticated/hasRole). Le scoping fin reste dans {@link PunchCardService} :
 * <ul>
 *   <li>{@code GET /} — VIEW:PUNCH_CARDS, self-scope : le membre ne voit QUE ses cartes
 *       (filtre {@code (tenant, client courant)} côté service).</li>
 *   <li>{@code POST /{id}/redeem} — UPDATE:PUNCH_CARDS + ABAC staff/admin only (le service
 *       refuse 403 à un membre, applique une séance gratuite si un palier est dispo, 422 sinon).</li>
 * </ul>
 *
 * <p>Pas d'endpoint CREATE/DELETE : une carte naît et grandit via l'auto-punch
 * ({@code ResourceBookingPunchListener} sur booking {@code completed}), pas à la main.</p>
 */
@RestController
@RequestMapping("/api/punch-cards")
@Tag(name = "PunchCards", description = "Cartes de fidélité punch 10/1 (PCC §11 — padel/tennis/spa/golf/coiffeur/palm gym)")
@RequiredArgsConstructor
public class PunchCardController {

    private final PunchCardService service;

    @GetMapping
    @Operation(summary = "Mes cartes de fidélité (self-scope) — 1 par activité")
    @PreAuthorize("hasAuthority('VIEW:PUNCH_CARDS')")
    public List<PunchCardDto> myCards() {
        return service.listMyCards();
    }

    @PostMapping("/{id}/redeem")
    @Operation(summary = "Le staff applique une séance gratuite (redeem un palier complet)")
    @PreAuthorize("hasAuthority('UPDATE:PUNCH_CARDS')")
    public PunchCardDto redeem(@PathVariable UUID id) {
        return service.redeem(id);
    }

    @GetMapping("/by-tenant/{tenantId}")
    @Operation(summary = "Toutes les cartes d'un tenant avec identité membre (export STAFF — Gap #3)")
    @PreAuthorize("hasAuthority('VIEW:STAFF')")
    public List<PunchCardAdminDto> byTenant(@PathVariable UUID tenantId) {
        return service.listByTenant(tenantId);
    }
}
