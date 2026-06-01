package com.onesley.oneclick.modules.financial;

import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.financial.api.FinancialDtos.*;
import com.onesley.oneclick.modules.financial.api.FinancialDtos;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractUpdateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceLineCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceLineDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceUpdateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.WalletTxCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.WalletTxDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplatePatchDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateArticleCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateArticleDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateArticlePatchDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.DisabledArticlesDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractRenewResultDto;
import com.onesley.oneclick.modules.financial.internal.FinancialCronJobs;
import com.onesley.oneclick.modules.financial.internal.FinancialService;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/financial")
@Tag(name = "Financial", description = "Contrats, factures, lignes de facture, wallet transactions (§5)")
@RequiredArgsConstructor
public class FinancialController {

    private final FinancialService service;
    private final FinancialCronJobs cronJobs;
    private final RestaurantAccessGuard restaurantAccessGuard;

    /**
     * P2 owner-check : les listes financières (contrats/factures/wallet) ne doivent
     * pas exposer TOUS les restaurants. Un non-admin doit cibler un restaurant dont
     * il est staff actif (les détails par-id sont déjà verrouillés côté service).
     */
    private void scopeFinancialList(UUID restaurantId) {
        if (!SecurityHelper.isAdmin()) {
            restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        }
    }

    /**
     * Trigger manuel de la génération mensuelle des factures — Sprint I.3.
     *
     * <p>Port EF {@code generate-invoices}. Le cron `generateMonthlyInvoicesCron`
     * s'exécute automatiquement le 1er du mois à 3h UTC ; cet endpoint permet
     * à l'admin de re-générer manuellement (idempotent : skip restos déjà facturés).
     *
     * @param periodMonth format "yyyy-MM" (ex: "2026-04" pour avril 2026)
     * @return nombre de factures générées
     */
    // Bug 32 (Batch C RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:FINANCIAL')
    @PostMapping("/invoices/generate-monthly")
    @Operation(summary = "Sprint I.3 — Trigger manuel cron génération factures mensuelles")
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public java.util.Map<String, Object> generateMonthlyInvoices(@RequestParam String periodMonth) {
        int generated = cronJobs.generateInvoicesForPeriod(periodMonth);
        return java.util.Map.of(
            "periodMonth", periodMonth,
            "generated", generated,
            "message", generated + " factures générées pour " + periodMonth
        );
    }

    // ─── Contracts ───────────────────────────────────────────────────────────

    @GetMapping("/contracts")
    @Operation(summary = "Contrats paginés — filtres restaurantId / status")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public PageResponse<ContractDto> findAllContracts(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        scopeFinancialList(restaurantId);
        return PageResponse.from(service.findAllContracts(restaurantId, status, page, size));
    }

    @GetMapping("/contracts/{id}")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public ContractDto findContractById(@PathVariable UUID id) { return service.findContractById(id); }

    @PostMapping("/contracts")
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public ResponseEntity<ContractDto> createContract(@Valid @RequestBody ContractCreateDto dto) {
        ContractDto c = service.createContract(dto);
        return ResponseEntity.created(URI.create("/api/financial/contracts/" + c.id())).body(c);
    }

    @PatchMapping("/contracts/{id}")
    @PreAuthorize("hasAuthority('UPDATE:FINANCIAL')")
    public ContractDto updateContract(@PathVariable UUID id, @Valid @RequestBody ContractUpdateDto dto) {
        return service.updateContract(id, dto);
    }

    @GetMapping("/contracts/{id}/history")
    @Operation(summary = "Timeline d'audit d'un contrat (V59) — modifications de champs suivis")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public List<ContractHistoryDto> findContractHistory(@PathVariable UUID id) {
        return service.findContractHistory(id);
    }

    // ─── Invoices ────────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    @Operation(summary = "Factures paginées — filtres restaurantId / status")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public PageResponse<InvoiceDto> findAllInvoices(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        scopeFinancialList(restaurantId);
        return PageResponse.from(service.findAllInvoices(restaurantId, status, page, size));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public InvoiceDto findInvoiceById(@PathVariable UUID id) { return service.findInvoiceById(id); }

    @PostMapping("/invoices")
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public ResponseEntity<InvoiceDto> createInvoice(@Valid @RequestBody InvoiceCreateDto dto) {
        InvoiceDto i = service.createInvoice(dto);
        return ResponseEntity.created(URI.create("/api/financial/invoices/" + i.id())).body(i);
    }

    @PatchMapping("/invoices/{id}")
    @Operation(summary = "Mise à jour totaux / status. status=paid → paid_at automatique.")
    @PreAuthorize("hasAuthority('UPDATE:FINANCIAL')")
    public InvoiceDto updateInvoice(@PathVariable UUID id, @Valid @RequestBody InvoiceUpdateDto dto) {
        return service.updateInvoice(id, dto);
    }

    // ─── Invoice lines ───────────────────────────────────────────────────────

    @GetMapping("/invoices/{invoiceId}/lines")
    @Operation(summary = "Lignes d'une facture — line_total = quantity × unit_price (GENERATED).")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public List<InvoiceLineDto> findLinesByInvoice(@PathVariable UUID invoiceId) {
        return service.findLinesByInvoice(invoiceId);
    }

    @PostMapping("/lines")
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public ResponseEntity<InvoiceLineDto> createLine(@Valid @RequestBody InvoiceLineCreateDto dto) {
        InvoiceLineDto l = service.createLine(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(l);
    }

    // ─── Wallet transactions ─────────────────────────────────────────────────

    @GetMapping("/wallet-tx")
    @Operation(summary = "Mouvements wallet paginés — filtres restaurantId / type")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public PageResponse<WalletTxDto> findAllWalletTx(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String type,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        scopeFinancialList(restaurantId);
        return PageResponse.from(service.findAllWalletTx(restaurantId, type, page, size));
    }

    @GetMapping("/wallet/balances")
    @Operation(
        summary = "Solde wallet par restaurant en UNE requête (B1.5 — anti N+1)",
        description = "Remplace le fan-out par-resto de RestaurantDetailsDialog. VIEW:FINANCIAL " +
                      "+ ABAC : admin → tout ; sinon staff actif de CHAQUE resto demandé. Restaurants " +
                      "sans mouvement absents du résultat (le front traite l'absence comme 0)."
    )
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public List<WalletBalanceDto> walletBalances(
        @RequestParam @Size(max = 1000) List<UUID> restaurantIds
    ) {
        restaurantIds.forEach(this::scopeFinancialList);
        return service.walletBalancesByRestaurants(restaurantIds);
    }

    @PostMapping("/wallet-tx")
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public ResponseEntity<WalletTxDto> createWalletTx(@Valid @RequestBody WalletTxCreateDto dto) {
        WalletTxDto t = service.createWalletTx(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(t);
    }

    // ─── Contract templates (V13) ────────────────────────────────────────────

    @GetMapping("/contract-templates")
    @Operation(summary = "Liste des templates contractuels — filtres tenantId / language / isActive")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public List<ContractTemplateDto> findAllContractTemplates(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) String language,
        @RequestParam(required = false) Boolean isActive
    ) {
        return service.findAllContractTemplates(tenantId, language, isActive);
    }

    @GetMapping("/contract-templates/{id}")
    @Operation(summary = "Détail template par UUID (admin only)")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public ContractTemplateDto findContractTemplateById(@PathVariable UUID id) {
        return service.findContractTemplateById(id);
    }

    @GetMapping("/contract-templates/by-code/{code}")
    @Operation(summary = "Résolution par code (ContractDownload PDF) — fallback platform si tenant manquant")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public ContractTemplateDto findContractTemplateByCode(
        @PathVariable String code,
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false, defaultValue = "fr") String language,
        @RequestParam(required = false, defaultValue = "1") Integer version
    ) {
        return service.findContractTemplateByCode(tenantId, code, language, version);
    }

    @PostMapping("/contract-templates")
    @Operation(summary = "Crée un template contractuel (admin only)")
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public ResponseEntity<ContractTemplateDto> createContractTemplate(
        @Valid @RequestBody ContractTemplateCreateDto dto
    ) {
        ContractTemplateDto created = service.createContractTemplate(dto);
        return ResponseEntity
            .created(URI.create("/api/financial/contract-templates/" + created.id()))
            .body(created);
    }

    @PatchMapping("/contract-templates/{id}")
    @Operation(summary = "Mise à jour partielle d'un template (admin only)")
    // Templates = config plateforme GLOBALE (pas de restaurant owner) → admin only,
    // comme create/delete. CREATE:FINANCIAL n'est PAS détenu par RESTAURATEUR (qui a
    // UPDATE:FINANCIAL) → on l'utilise ici pour fermer la mutation des templates au resto.
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public ContractTemplateDto patchContractTemplate(
        @PathVariable UUID id, @Valid @RequestBody ContractTemplatePatchDto dto
    ) {
        return service.patchContractTemplate(id, dto);
    }

    @DeleteMapping("/contract-templates/{id}")
    @Operation(summary = "Soft delete d'un template (admin only)")
    @PreAuthorize("hasAuthority('DELETE:FINANCIAL')")
    public ResponseEntity<Void> deleteContractTemplate(@PathVariable UUID id) {
        service.softDeleteContractTemplate(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Contract template articles (clauses, V55) ─────────────────────────────

    @GetMapping("/contract-templates/{templateId}/articles")
    @Operation(summary = "Liste des articles (clauses) d'un template, ordonnés sort_order")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public List<ContractTemplateArticleDto> findTemplateArticles(@PathVariable UUID templateId) {
        return service.findTemplateArticles(templateId);
    }

    @PostMapping("/contract-templates/{templateId}/articles")
    @Operation(summary = "Crée un article de template (admin only)")
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public ResponseEntity<ContractTemplateArticleDto> createTemplateArticle(
        @PathVariable UUID templateId, @Valid @RequestBody ContractTemplateArticleCreateDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTemplateArticle(templateId, dto));
    }

    @PatchMapping("/contract-templates/{templateId}/articles/{articleId}")
    @Operation(summary = "Mise à jour partielle d'un article (admin only)")
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public ContractTemplateArticleDto patchTemplateArticle(
        @PathVariable UUID templateId, @PathVariable UUID articleId, @Valid @RequestBody ContractTemplateArticlePatchDto dto
    ) {
        return service.patchTemplateArticle(articleId, dto);
    }

    @DeleteMapping("/contract-templates/{templateId}/articles/{articleId}")
    @Operation(summary = "Supprime un article de template (admin only)")
    @PreAuthorize("hasAuthority('DELETE:FINANCIAL')")
    public ResponseEntity<Void> deleteTemplateArticle(@PathVariable UUID templateId, @PathVariable UUID articleId) {
        service.deleteTemplateArticle(articleId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Articles désactivés par contrat (V56) ─────────────────────────────────

    @GetMapping("/contracts/{contractId}/disabled-articles")
    @Operation(summary = "Liste des IDs d'articles désactivés pour un contrat")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public List<UUID> findDisabledArticles(@PathVariable UUID contractId) {
        return service.findDisabledArticleIds(contractId);
    }

    @PutMapping("/contracts/{contractId}/disabled-articles")
    @Operation(summary = "Remplace l'ensemble des articles désactivés d'un contrat")
    @PreAuthorize("hasAuthority('UPDATE:FINANCIAL')")
    public List<UUID> setDisabledArticles(@PathVariable UUID contractId, @RequestBody DisabledArticlesDto dto) {
        return service.setDisabledArticles(contractId, dto.articleIds());
    }

    // ─── Renouvellement en masse des contrats auto (V57 — port renew-contracts) ─

    @PostMapping("/contracts/renew")
    @Operation(summary = "Renouvelle les contrats actifs en auto-renew arrivant à échéance (admin only)")
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public ContractRenewResultDto renewContracts() {
        return service.renewContracts();
    }
}
