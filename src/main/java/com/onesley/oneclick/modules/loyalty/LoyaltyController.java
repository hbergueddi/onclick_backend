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

@RestController
@RequestMapping("/api/loyalty")
@Tag(name = "Loyalty", description = "Comptes + transactions fidélité (§6)")
public class LoyaltyController {

    private final LoyaltyService service;
    private final OcrReceiptService ocrService;
    private final GainRuleRequestService gainRuleRequestService;

    public LoyaltyController(LoyaltyService service,
                             OcrReceiptService ocrService,
                             GainRuleRequestService gainRuleRequestService) {
        this.service = service;
        this.ocrService = ocrService;
        this.gainRuleRequestService = gainRuleRequestService;
    }

    public record SpendDto(
        @NotNull UUID clientId,
        @NotNull UUID restaurantId,
        @NotNull @Min(1) Integer points,
        String reason
    ) {}

    @GetMapping("/accounts/{id}")
    @Operation(summary = "Détail d'un compte fidélité")
    @PreAuthorize("isAuthenticated()")
    public LoyaltyAccountDto findAccount(@PathVariable UUID id) {
        return service.findAccount(id);
    }

    @GetMapping("/accounts")
    @Operation(summary = "Comptes fidélité d'un client (lookup ou création auto si besoin)")
    @PreAuthorize("isAuthenticated()")
    public LoyaltyAccountDto findOrCreate(
        @RequestParam UUID clientId,
        @RequestParam UUID restaurantId
    ) {
        return service.findOrCreate(clientId, restaurantId);
    }

    @GetMapping("/accounts/by-client/{clientId}")
    @Operation(summary = "Tous les comptes fidélité d'un client (1 par restaurant)")
    @PreAuthorize("isAuthenticated()")
    public List<LoyaltyAccountDto> findByClient(@PathVariable UUID clientId) {
        SecurityHelper.requireOwnerOrAdmin(clientId);
        return service.findByClient(clientId);
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @Operation(summary = "Historique des mouvements d'un compte")
    @PreAuthorize("isAuthenticated()")
    public List<LoyaltyTransactionDto> findTransactionsByAccount(@PathVariable UUID accountId) {
        return service.findTransactionsByAccount(accountId);
    }

    @PostMapping("/earn")
    @Operation(summary = "Crédite des points (Snap2Earn). INSERT transaction + UPDATE balance dans la même tx.")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public LoyaltyTransactionDto earn(@Valid @RequestBody LoyaltyEarnDto dto) {
        return service.earnPoints(dto);
    }

    @PostMapping("/snap2earn")
    @Operation(summary = "Snap2Earn — orchestrateur : lookup gain rule + calcul points + earn() atomic. Anti-doublon par (restaurantId, ticketRef).")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public Snap2EarnResultDto snap2earn(@Valid @RequestBody Snap2EarnDto dto) {
        return service.snap2earn(dto);
    }

    @PostMapping("/ocr-receipt")
    @Operation(summary = "OCR ticket caisse — appel OCR.space + extraction montant via regex. Stub mode si OCR_SPACE_API_KEY absente.")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public OcrReceiptResultDto ocrReceipt(@Valid @RequestBody OcrReceiptRequestDto dto) {
        return ocrService.ocr(dto);
    }

    @PostMapping("/spend")
    @Operation(summary = "Débite des points (Redemption). Refuse si solde insuffisant.")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
    public LoyaltyTransactionDto giftPoints(@Valid @RequestBody GiftPointsDto dto) {
        UUID senderId = SecurityHelper.currentUserId();
        return service.giftPoints(senderId, dto);
    }

    // ─── Gain rules (par-restaurant) ─────────────────────────────────────────

    @GetMapping("/gain-rules/by-restaurant/{restaurantId}")
    @Operation(summary = "Règle de gain de points d'un restaurant (catalogue public).")
    @PreAuthorize("isAuthenticated()")
    public GainRuleDto findGainRuleByRestaurant(@PathVariable UUID restaurantId) {
        return service.findGainRuleByRestaurant(restaurantId);
    }

    @PostMapping("/gain-rules")
    @Operation(summary = "Crée une règle de gain pour un restaurant (1 par resto via UNIQUE).")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public GainRuleDto createGainRule(@Valid @RequestBody GainRuleCreateDto dto) {
        return service.createGainRule(dto);
    }

    @PatchMapping("/gain-rules/{id}")
    @Operation(summary = "Modifie une règle de gain (PATCH partiel).")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public GainRuleDto patchGainRule(@PathVariable UUID id, @Valid @RequestBody GainRulePatchDto dto) {
        return service.patchGainRule(id, dto);
    }

    @DeleteMapping("/gain-rules/{id}")
    @Operation(summary = "Soft delete d'une règle de gain.")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public void deleteGainRule(@PathVariable UUID id) {
        service.deleteGainRule(id);
    }

    // ─── Helpers loyalty pour Pocket ─────────────────────────────────────────

    @GetMapping("/transactions/by-client/{clientId}")
    @Operation(summary = "Toutes les transactions fidélité d'un client (cross-comptes, anti-N+1).")
    @PreAuthorize("isAuthenticated()")
    public List<LoyaltyTransactionDto> findTransactionsByClient(
        @PathVariable UUID clientId,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(500) Integer limit
    ) {
        return service.findTransactionsByClient(clientId, limit);
    }

    @GetMapping("/expired-points/by-client/{clientId}")
    @Operation(summary = "Points expirés d'un client (toutes comptes confondus).")
    @PreAuthorize("isAuthenticated()")
    public ExpiredPointsSummaryDto findExpiredPointsByClient(@PathVariable UUID clientId) {
        return service.findExpiredPointsByClient(clientId);
    }

    @GetMapping("/transactions/by-restaurant/{restaurantId}")
    @Operation(
        summary = "Sprint G.2.8 — Toutes les transactions fidélité d'un restaurant (anti-N+1).",
        description = "Utilisé par ProDesk ClientSummary/StaffSummary pour agréger crédité/consommé."
    )
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public List<LoyaltyTransactionDto> findTransactionsByRestaurant(
        @PathVariable UUID restaurantId,
        @RequestParam(required = false, defaultValue = "200") @Min(1) @Max(2000) Integer limit
    ) {
        return service.findTransactionsByRestaurant(restaurantId, limit);
    }

    @GetMapping("/tiers")
    @Operation(summary = "Liste tous les paliers de fidélité (toutes tenants confondus).")
    @PreAuthorize("isAuthenticated()")
    public List<TierDto> listTiers() {
        return service.listTiers();
    }

    @GetMapping("/tiers/by-tenant/{tenantId}")
    @Operation(summary = "Paliers de fidélité d'un tenant spécifique.")
    @PreAuthorize("isAuthenticated()")
    public List<TierDto> listTiersByTenant(@PathVariable UUID tenantId) {
        return service.listTiersByTenant(tenantId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Gain rule requests — workflow approbation admin (Sprint G.2.3)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/gain-rule-requests")
    @Operation(summary = "Liste toutes les demandes de règle (admin platform-wide)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public List<GainRuleRequestDto> listGainRuleRequests(
        @RequestParam(required = false, defaultValue = "false") boolean pendingOnly
    ) {
        return pendingOnly
            ? gainRuleRequestService.findPending()
            : gainRuleRequestService.findAll();
    }

    @GetMapping("/gain-rule-requests/by-restaurant/{restaurantId}")
    @Operation(summary = "Demandes d'un restaurant spécifique (restaurateur)")
    @PreAuthorize("isAuthenticated()")
    public List<GainRuleRequestDto> findGainRuleRequestsByRestaurant(@PathVariable UUID restaurantId) {
        return gainRuleRequestService.findByRestaurant(restaurantId);
    }

    @GetMapping("/gain-rule-requests/{id}")
    @Operation(summary = "Détail d'une demande de règle")
    @PreAuthorize("isAuthenticated()")
    public GainRuleRequestDto findGainRuleRequestById(@PathVariable UUID id) {
        return gainRuleRequestService.findById(id);
    }

    @PostMapping("/gain-rule-requests")
    @Operation(summary = "Crée une demande de règle de gain (restaurateur)")
    @PreAuthorize("hasAnyRole('RESTAURATEUR','GROUP_ADMIN','SUPERADMIN')")
    public GainRuleRequestDto createGainRuleRequest(
        @Valid @RequestBody GainRuleRequestDto.CreateDto dto
    ) {
        return gainRuleRequestService.create(dto);
    }

    @PatchMapping("/gain-rule-requests/{id}/approve")
    @Operation(summary = "Approuve une demande → crée la GainRule (désactivée)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public GainRuleRequestDto approveGainRuleRequest(@PathVariable UUID id) {
        return gainRuleRequestService.approve(id);
    }

    @PatchMapping("/gain-rule-requests/{id}/reject")
    @Operation(summary = "Refuse une demande avec motif")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public GainRuleRequestDto rejectGainRuleRequest(
        @PathVariable UUID id,
        @Valid @RequestBody GainRuleRequestDto.RejectDto dto
    ) {
        return gainRuleRequestService.reject(id, dto);
    }
}
