package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.modules.loyalty.api.ExpiredPointsSummaryDto;
import com.onesley.oneclick.modules.loyalty.api.GainRuleCreateDto;
import com.onesley.oneclick.modules.loyalty.api.GainRuleDto;
import com.onesley.oneclick.modules.loyalty.api.GainRulePatchDto;
import com.onesley.oneclick.modules.loyalty.api.GainRuleRequestDto;
import com.onesley.oneclick.modules.loyalty.api.GiftPointsDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyAccountDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyEarnDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTransactionDto;
import com.onesley.oneclick.modules.loyalty.api.OcrReceiptRequestDto;
import com.onesley.oneclick.modules.loyalty.api.OcrReceiptResultDto;
import com.onesley.oneclick.modules.loyalty.api.Snap2EarnDto;
import com.onesley.oneclick.modules.loyalty.api.Snap2EarnResultDto;
import com.onesley.oneclick.modules.loyalty.api.TierDto;
import com.onesley.oneclick.modules.loyalty.internal.GainRuleRequestService;
import com.onesley.oneclick.modules.loyalty.internal.LoyaltyService;
import com.onesley.oneclick.modules.loyalty.internal.OcrReceiptService;
import com.onesley.oneclick.security.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * REST controller {@code /api/loyalty} — Bug 32 (Batch A RBAC v2).
 *
 * <p>Tous les endpoints en RBAC v2 senior strict {@code hasAuthority('VERB:RESOURCE')}. Le scoping fin (ownership compte) reste
 * géré dans {@link LoyaltyService} via {@code SecurityHelper.requireOwnerOrAdmin}.
 */
@RestController
@RequestMapping("/api/loyalty")
@Tag(name = "Loyalty", description = "Comptes + transactions fidélité (§6)")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService service;
    private final OcrReceiptService ocrService;
    private final GainRuleRequestService gainRuleRequestService;

    public record SpendDto(
        @NotNull UUID clientId,
        @NotNull UUID restaurantId,
        @NotNull @Min(1) Integer points,
        String reason
    ) {}

    @GetMapping("/accounts/{id}")
    @Operation(summary = "Détail d'un compte fidélité")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public LoyaltyAccountDto findAccount(@PathVariable UUID id) {
        return service.findAccount(id);
    }

    @GetMapping("/accounts")
    @Operation(summary = "Comptes fidélité d'un client (lookup ou création auto si besoin)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public LoyaltyAccountDto findOrCreate(
        @RequestParam UUID clientId,
        @RequestParam UUID restaurantId
    ) {
        return service.findOrCreate(clientId, restaurantId);
    }

    @GetMapping("/accounts/by-client/{clientId}")
    @Operation(summary = "Tous les comptes fidélité d'un client (1 par restaurant)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<LoyaltyAccountDto> findByClient(@PathVariable UUID clientId) {
        SecurityHelper.requireOwnerOrAdmin(clientId);
        return service.findByClient(clientId);
    }

    @GetMapping("/accounts/by-restaurant/{restaurantId}")
    @Operation(
        summary = "Bug 28 — Tous les comptes fidélité d'un restaurant (1 par client fréquentant).",
        description = "Utilisé par PulsePro Dashboard Client pour le KPI 'Solde Disponible' " +
                      "et la colonne SOLDE du Top 10 — la balance live ne peut pas être dérivée des " +
                      "seules transactions car les seeds ont rempli accounts.balance directement."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<LoyaltyAccountDto> findAccountsByRestaurant(@PathVariable UUID restaurantId) {
        return service.findAccountsByRestaurant(restaurantId);
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @Operation(summary = "Historique des mouvements d'un compte")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<LoyaltyTransactionDto> findTransactionsByAccount(@PathVariable UUID accountId) {
        return service.findTransactionsByAccount(accountId);
    }

    @PostMapping("/earn")
    @Operation(summary = "Crédite des points (Snap2Earn). INSERT transaction + UPDATE balance dans la même tx.")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public LoyaltyTransactionDto earn(@Valid @RequestBody LoyaltyEarnDto dto) {
        return service.earnPoints(dto);
    }

    @PostMapping("/snap2earn")
    @Operation(summary = "Snap2Earn — orchestrateur : lookup gain rule + calcul points + earn() atomic. Anti-doublon par (restaurantId, ticketRef).")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public Snap2EarnResultDto snap2earn(@Valid @RequestBody Snap2EarnDto dto) {
        return service.snap2earn(dto);
    }

    @PostMapping("/ocr-receipt")
    @Operation(summary = "OCR ticket caisse — appel OCR.space + extraction montant via regex. Stub mode si OCR_SPACE_API_KEY absente.")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public OcrReceiptResultDto ocrReceipt(@Valid @RequestBody OcrReceiptRequestDto dto) {
        return ocrService.ocr(dto);
    }

    @PostMapping("/spend")
    @Operation(summary = "Débite des points (Redemption). Refuse si solde insuffisant.")
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY')")
    public LoyaltyTransactionDto spend(@Valid @RequestBody SpendDto dto) {
        SecurityHelper.requireOwnerOrAdmin(dto.clientId());
        return service.spendPoints(dto.clientId(), dto.restaurantId(), dto.points(), dto.reason());
    }

    @PostMapping("/gift")
    @Operation(
        summary = "Sprint G.5 — Gift points (port EF gift-points)",
        description = "Le user authentifié offre des points à un ami pour un restaurant. " +
                      "Débit sender + crédit receiver atomique."
    )
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY')")
    public LoyaltyTransactionDto giftPoints(@Valid @RequestBody GiftPointsDto dto) {
        UUID senderId = SecurityHelper.currentUserId();
        return service.giftPoints(senderId, dto);
    }

    // ─── Gain rules (par-restaurant) ─────────────────────────────────────────

    @GetMapping("/gain-rules/by-restaurant/{restaurantId}")
    @Operation(summary = "Règle de gain de points d'un restaurant (catalogue public).")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public GainRuleDto findGainRuleByRestaurant(@PathVariable UUID restaurantId) {
        return service.findGainRuleByRestaurant(restaurantId);
    }

    @PostMapping("/gain-rules")
    @Operation(summary = "Crée une règle de gain pour un restaurant (1 par resto via UNIQUE).")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public GainRuleDto createGainRule(@Valid @RequestBody GainRuleCreateDto dto) {
        // Config restaurant = gérant/admin uniquement. CREATE:LOYALTY (V35) est
        // accordé à STAFF pour le Snap2Earn, mais le staff ne configure pas les
        // règles de gain → on referme finement (cf SecurityHelper.requireManagerOrAdmin).
        SecurityHelper.requireManagerOrAdmin();
        return service.createGainRule(dto);
    }

    @PatchMapping("/gain-rules/{id}")
    @Operation(summary = "Modifie une règle de gain (PATCH partiel).")
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY')")
    public GainRuleDto patchGainRule(@PathVariable UUID id, @Valid @RequestBody GainRulePatchDto dto) {
        return service.patchGainRule(id, dto);
    }

    @DeleteMapping("/gain-rules/{id}")
    @Operation(summary = "Soft delete d'une règle de gain.")
    @PreAuthorize("hasAuthority('DELETE:LOYALTY')")
    public void deleteGainRule(@PathVariable UUID id) {
        service.deleteGainRule(id);
    }

    // ─── Helpers loyalty pour Pocket ─────────────────────────────────────────

    @GetMapping("/transactions/by-client/{clientId}")
    @Operation(summary = "Toutes les transactions fidélité d'un client (cross-comptes, anti-N+1).")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<LoyaltyTransactionDto> findTransactionsByClient(
        @PathVariable UUID clientId,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(500) Integer limit
    ) {
        return service.findTransactionsByClient(clientId, limit);
    }

    @GetMapping("/expired-points/by-client/{clientId}")
    @Operation(summary = "Points expirés d'un client (toutes comptes confondus).")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public ExpiredPointsSummaryDto findExpiredPointsByClient(@PathVariable UUID clientId) {
        return service.findExpiredPointsByClient(clientId);
    }

    @GetMapping("/transactions/by-restaurant/{restaurantId}")
    @Operation(
        summary = "Sprint G.2.8 — Toutes les transactions fidélité d'un restaurant (anti-N+1).",
        description = "Utilisé par ProDesk ClientSummary/StaffSummary pour agréger crédité/consommé."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<LoyaltyTransactionDto> findTransactionsByRestaurant(
        @PathVariable UUID restaurantId,
        @RequestParam(required = false, defaultValue = "200") @Min(1) @Max(2000) Integer limit
    ) {
        return service.findTransactionsByRestaurant(restaurantId, limit);
    }

    @GetMapping("/tiers")
    @Operation(summary = "Liste tous les paliers de fidélité (toutes tenants confondus).")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<TierDto> listTiers() {
        return service.listTiers();
    }

    @GetMapping("/tiers/by-tenant/{tenantId}")
    @Operation(summary = "Paliers de fidélité d'un tenant spécifique.")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<TierDto> listTiersByTenant(@PathVariable UUID tenantId) {
        return service.listTiersByTenant(tenantId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Gain rule requests — workflow approbation admin (Sprint G.2.3)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/gain-rule-requests")
    @Operation(summary = "Liste toutes les demandes de règle (admin platform-wide)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<GainRuleRequestDto> listGainRuleRequests(
        @RequestParam(required = false, defaultValue = "false") boolean pendingOnly
    ) {
        return pendingOnly
            ? gainRuleRequestService.findPending()
            : gainRuleRequestService.findAll();
    }

    @GetMapping("/gain-rule-requests/by-restaurant/{restaurantId}")
    @Operation(summary = "Demandes d'un restaurant spécifique (restaurateur)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<GainRuleRequestDto> findGainRuleRequestsByRestaurant(@PathVariable UUID restaurantId) {
        return gainRuleRequestService.findByRestaurant(restaurantId);
    }

    @GetMapping("/gain-rule-requests/{id}")
    @Operation(summary = "Détail d'une demande de règle")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public GainRuleRequestDto findGainRuleRequestById(@PathVariable UUID id) {
        return gainRuleRequestService.findById(id);
    }

    @PostMapping("/gain-rule-requests")
    @Operation(summary = "Crée une demande de règle de gain (restaurateur)")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public GainRuleRequestDto createGainRuleRequest(
        @Valid @RequestBody GainRuleRequestDto.CreateDto dto
    ) {
        // Demande de gain rule = gérant/admin (le restaurateur soumet, l'admin approuve).
        // Refermé pour le STAFF qui détient CREATE:LOYALTY au titre du Snap2Earn (V35).
        SecurityHelper.requireManagerOrAdmin();
        return gainRuleRequestService.create(dto);
    }

    @PatchMapping("/gain-rule-requests/{id}/approve")
    @Operation(summary = "Approuve une demande → crée la GainRule (désactivée)")
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY')")
    public GainRuleRequestDto approveGainRuleRequest(@PathVariable UUID id) {
        return gainRuleRequestService.approve(id);
    }

    @PatchMapping("/gain-rule-requests/{id}/reject")
    @Operation(summary = "Refuse une demande avec motif")
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY')")
    public GainRuleRequestDto rejectGainRuleRequest(
        @PathVariable UUID id,
        @Valid @RequestBody GainRuleRequestDto.RejectDto dto
    ) {
        return gainRuleRequestService.reject(id, dto);
    }
}
