package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.modules.loyalty.api.ClientNameDto;
import com.onesley.oneclick.modules.loyalty.api.ExpiredPointsSummaryDto;
import com.onesley.oneclick.modules.loyalty.api.GainRuleCreateDto;
import com.onesley.oneclick.modules.loyalty.api.GainRuleDto;
import com.onesley.oneclick.modules.loyalty.api.GainRulePatchDto;
import com.onesley.oneclick.modules.loyalty.api.GainRuleRequestDto;
import com.onesley.oneclick.modules.loyalty.api.GiftPointsDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyAccountDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyEarnDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyParamsDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTransactionDto;
import com.onesley.oneclick.modules.loyalty.api.ScannedTicketStatsDto;
import com.onesley.oneclick.modules.loyalty.api.ScannedTicketDto;
import com.onesley.oneclick.modules.loyalty.api.OcrReceiptRequestDto;
import com.onesley.oneclick.modules.loyalty.api.OcrReceiptResultDto;
import com.onesley.oneclick.modules.loyalty.api.Snap2EarnDto;
import com.onesley.oneclick.modules.loyalty.api.Snap2EarnResultDto;
import com.onesley.oneclick.modules.loyalty.api.TierDto;
import com.onesley.oneclick.modules.loyalty.api.TierUpdateDto;
import com.onesley.oneclick.modules.loyalty.internal.GainRuleRequestService;
import com.onesley.oneclick.modules.loyalty.internal.LoyaltyService;
import com.onesley.oneclick.modules.loyalty.internal.OcrReceiptService;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import com.onesley.oneclick.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class LoyaltyController {

    private final LoyaltyService service;
    private final OcrReceiptService ocrService;
    private final GainRuleRequestService gainRuleRequestService;
    private final RestaurantAccessGuard restaurantAccessGuard;
    private final com.onesley.oneclick.modules.loyalty.internal.RedemptionOtpService redemptionOtpService;

    /**
     * P2 owner-check : un compte fidélité appartient à (client, restaurant).
     * Accès = le client lui-même, OU un staff actif / admin du restaurant du compte.
     * Sans cela, VIEW:LOYALTY (détenu par CLIENT) laissait lire le solde d'autrui.
     */
    private void requireAccountAccess(UUID clientId, UUID restaurantId) {
        if (clientId != null && clientId.equals(SecurityHelper.currentUserId())) return;
        if (restaurantAccessGuard.isAdminOrActiveStaffOf(restaurantId)) return;
        throw new ForbiddenException(
            "Accès interdit : compte fidélité d'un autre client / restaurant");
    }

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
        LoyaltyAccountDto account = service.findAccount(id);
        requireAccountAccess(account.clientId(), account.restaurantId());
        return account;
    }

    @GetMapping("/accounts")
    @Operation(summary = "Comptes fidélité d'un client (lookup ou création auto si besoin)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public LoyaltyAccountDto findOrCreate(
        @RequestParam UUID clientId,
        @RequestParam UUID restaurantId
    ) {
        requireAccountAccess(clientId, restaurantId);
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
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.findAccountsByRestaurant(restaurantId);
    }

    @GetMapping("/accounts/by-restaurants")
    @Operation(
        summary = "P1 anti-N+1 — comptes fidélité de PLUSIEURS restaurants (vue groupe/staff)",
        description = "Remplace le fan-out par-restaurant (shim Supabase client.ts:321/356 + PulsePro). "
                    + "ABAC : admin OU staff actif de CHAQUE restaurant demandé (403 sinon)."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<LoyaltyAccountDto> findAccountsByRestaurants(
        @RequestParam @Size(min = 1, max = 1000) List<UUID> restaurantIds
    ) {
        restaurantIds.forEach(restaurantAccessGuard::requireAdminOrActiveStaffOf);
        return service.findAccountsByRestaurants(restaurantIds);
    }

    @GetMapping("/transactions/by-restaurants")
    @Operation(
        summary = "P1 anti-N+1 — transactions fidélité enrichies de PLUSIEURS restaurants",
        description = "Remplace le fan-out par-restaurant (shim Supabase client.ts:285/321 + PulsePro). "
                    + "Projection enrichie clientId+restaurantId. ABAC : admin OU staff actif de CHAQUE resto."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<LoyaltyTransactionDto> findTransactionsByRestaurants(
        @RequestParam @Size(min = 1, max = 1000) List<UUID> restaurantIds,
        @RequestParam(required = false, defaultValue = "2000") @Min(1) @Max(10000) int limit
    ) {
        restaurantIds.forEach(restaurantAccessGuard::requireAdminOrActiveStaffOf);
        return service.findTransactionsByRestaurants(restaurantIds, limit);
    }

    /** Requête de résolution des noms clients pour les dashboards staff (PulsePro). */
    public record ClientNamesQuery(
        @NotEmpty List<UUID> restaurantIds,
        @NotEmpty List<UUID> clientIds
    ) {}

    @PostMapping("/clients/names")
    @Operation(
        summary = "Résout les noms des clients d'un restaurant (PulsePro Top clients) — scoped.",
        description = "Alternative à /api/users/by-ids (VIEW:USERS, refusé au RESTAURATEUR/STAFF). " +
                      "L'appelant doit être staff actif / admin de CHAQUE restaurant demandé ; ne renvoie " +
                      "que les clients ayant un compte fidélité à l'un de ces restaurants."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public List<ClientNameDto> resolveClientNames(@Valid @RequestBody ClientNamesQuery query) {
        for (UUID restaurantId : query.restaurantIds()) {
            restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        }
        return service.resolveClientNames(query.restaurantIds(), query.clientIds());
    }

    @GetMapping("/clients/search")
    @Operation(
        summary = "Recherche client Snap2Earn — par téléphone / nom (scopé staff actif du restaurant).",
        description = "Identification du porteur du ticket. CREATE:LOYALTY (détenu par le staff qui scanne) " +
                      "+ ABAC RestaurantAccessGuard. Alternative à /api/users (VIEW:USERS, admin-only). " +
                      "q &lt; 2 caractères → liste vide (évite les gros scans)."
    )
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public List<ClientNameDto> searchClients(
        @RequestParam String q,
        @RequestParam UUID restaurantId,
        @RequestParam(defaultValue = "8") int limit
    ) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        if (q == null || q.trim().length() < 2) return List.of();
        return service.searchClients(q.trim(), limit);
    }

    @GetMapping("/clients/by-code")
    @Operation(
        summary = "Résout un client par son Code OneClick (referral_code — QR / Carte Wallet) — scopé staff.",
        description = "Identification du porteur du ticket via le code scanné. CREATE:LOYALTY (détenu " +
                      "par le staff qui scanne) + ABAC RestaurantAccessGuard. Remplace le RPC legacy " +
                      "find_client_by_code. 404 si le code ne correspond à aucun client actif."
    )
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public ClientNameDto resolveClientByCode(
        @RequestParam String code,
        @RequestParam UUID restaurantId
    ) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.resolveClientByCode(code.trim());
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

    @PostMapping("/redemption-otp/request")
    @Operation(summary = "Demande un OTP de conversion (Gap #2) — notifie le client in-app avec le code. Le code n'est jamais renvoyé au staff.")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public com.onesley.oneclick.modules.loyalty.api.RedemptionOtpRequestDto requestRedemptionOtp(
        @Valid @RequestBody com.onesley.oneclick.modules.loyalty.api.RequestRedemptionOtpDto dto
    ) {
        return redemptionOtpService.requestOtp(
            dto.clientId(), dto.restaurantId(), dto.points(), dto.montant(), dto.discountDh());
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
    // Config loyalty = gérant/admin → UPDATE:LOYALTY (RESTAURATEUR/admin), PAS
    // CREATE:LOYALTY : le STAFF détient CREATE pour le Snap2Earn mais ne configure
    // pas les règles de gain. Séparation portée par l'authority (zéro check de rôle).
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY')")
    public GainRuleDto createGainRule(@Valid @RequestBody GainRuleCreateDto dto) {
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

    @GetMapping("/params")
    @Operation(
        summary = "Paramètres fidélité effectifs du client courant pour un restaurant (écran « Vos avantages » / ConversionGuide).",
        description = "Port du hook legacy useLoyaltyParams. Renvoie le taux de conversion effectif " +
                      "(gain_rules.conversion_rate × bonus palier), la valeur du point (loyalty_rules.point_value, " +
                      "défaut 1.0), la durée de validité (loyalty_rules.expires_after_days, défaut 365) et le palier " +
                      "courant + son bonus. ABAC self-scope STRICT : les params sont toujours calculés pour le user " +
                      "courant (SecurityHelper.currentUserId), jamais pour un autre client."
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public LoyaltyParamsDto loyaltyParams(@RequestParam UUID restaurantId) {
        // Self-scope : on ne lit JAMAIS les params d'un autre client. clientId = user courant.
        // VIEW:LOYALTY est l'autorité que détient CLIENT pour ses lectures fidélité (Pocket),
        // partagée avec staff/admin ; ici le scoping est garanti par currentUserId() (pas de
        // paramètre clientId exposé → aucune énumération possible).
        return service.resolveLoyaltyParams(SecurityHelper.currentUserId(), restaurantId);
    }

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
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.findTransactionsByRestaurant(restaurantId, limit);
    }

    @GetMapping("/scanned-tickets/stats")
    @Operation(
        summary = "Agrégat plateforme des tickets scannés (Snap2Earn) — PulseBoard admin.",
        description = "Cross-restaurant (count + points émis + CA scanné). Réservé admin " +
                      "(VIEW:ANALYTICS) comme les autres agrégats plateforme du dashboard."
    )
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public ScannedTicketStatsDto scannedTicketStats() {
        return service.scannedTicketStats();
    }

    @GetMapping("/scanned-tickets")
    @Operation(
        summary = "Liste plateforme des tickets scannés (Snap2Earn) — TrustWatch File de tickets.",
        description = "Cross-restaurant (ref + montant + points + resto + date). Réservé admin " +
                      "(VIEW:ANALYTICS). Distinct de /scanned-tickets/stats (agrégat)."
    )
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public List<ScannedTicketDto> scannedTickets(
        @RequestParam(required = false, defaultValue = "500") @Min(1) @Max(5000) Integer limit
    ) {
        return service.findScannedTickets(limit);
    }

    @GetMapping("/monthly-flows")
    @Operation(
        summary = "Flux mensuels de points sur 12 mois (pilotage fidélité) — gagnés/utilisés/expirés.",
        description = "Port RPC legacy get_loyalty_monthly_flows. Platform-wide, réservé admin (VIEW:ANALYTICS). " +
                      "Alimente le graphe 12 mois du tableau Pilotage (Gap #9)."
    )
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public List<com.onesley.oneclick.modules.loyalty.api.LoyaltyMonthlyFlowDto> monthlyFlows() {
        return service.monthlyFlows();
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

    @PatchMapping("/tiers/{id}")
    @Operation(summary = "Met à jour un palier de fidélité (name/minPoints/bonusPercent/sortOrder).")
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY_TIER')")
    public TierDto updateTier(@PathVariable UUID id, @Valid @RequestBody TierUpdateDto dto) {
        return service.updateTier(id, dto);
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
    // Demande de gain rule = gérant/admin (le restaurateur soumet, l'admin approuve)
    // → UPDATE:LOYALTY, pas CREATE:LOYALTY (qui est aussi détenu par STAFF pour le scan).
    @PreAuthorize("hasAuthority('UPDATE:LOYALTY')")
    public GainRuleRequestDto createGainRuleRequest(
        @Valid @RequestBody GainRuleRequestDto.CreateDto dto
    ) {
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
