package com.onesley.oneclick.modules.financial;

import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import com.onesley.oneclick.modules.financial.internal.FinancialService;

@RestController
@RequestMapping("/api/financial")
@Tag(name = "Financial", description = "Contrats, factures, lignes de facture, wallet transactions (§5)")
public class FinancialController {

    private final FinancialService service;

    public FinancialController(FinancialService service) {
        this.service = service;
    }

    // ─── Contracts ───────────────────────────────────────────────────────────

    @GetMapping("/contracts")
    @Operation(summary = "Contrats paginés — filtres restaurantId / status")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public PageResponse<ContractDto> findAllContracts(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllContracts(restaurantId, status, page, size));
    }

    @GetMapping("/contracts/{id}")
    @PreAuthorize("isAuthenticated()")
    public ContractDto findContractById(@PathVariable UUID id) { return service.findContractById(id); }

    @PostMapping("/contracts")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<ContractDto> createContract(@Valid @RequestBody ContractCreateDto dto) {
        ContractDto c = service.createContract(dto);
        return ResponseEntity.created(URI.create("/api/financial/contracts/" + c.id())).body(c);
    }

    @PatchMapping("/contracts/{id}")
    @PreAuthorize("isAuthenticated()")
    public ContractDto updateContract(@PathVariable UUID id, @Valid @RequestBody ContractUpdateDto dto) {
        return service.updateContract(id, dto);
    }

    // ─── Invoices ────────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    @Operation(summary = "Factures paginées — filtres restaurantId / status")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public PageResponse<InvoiceDto> findAllInvoices(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllInvoices(restaurantId, status, page, size));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("isAuthenticated()")
    public InvoiceDto findInvoiceById(@PathVariable UUID id) { return service.findInvoiceById(id); }

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<InvoiceDto> createInvoice(@Valid @RequestBody InvoiceCreateDto dto) {
        InvoiceDto i = service.createInvoice(dto);
        return ResponseEntity.created(URI.create("/api/financial/invoices/" + i.id())).body(i);
    }

    @PatchMapping("/invoices/{id}")
    @Operation(summary = "Mise à jour totaux / status. status=paid → paid_at automatique.")
    @PreAuthorize("isAuthenticated()")
    public InvoiceDto updateInvoice(@PathVariable UUID id, @Valid @RequestBody InvoiceUpdateDto dto) {
        return service.updateInvoice(id, dto);
    }

    // ─── Invoice lines ───────────────────────────────────────────────────────

    @GetMapping("/invoices/{invoiceId}/lines")
    @Operation(summary = "Lignes d'une facture — line_total = quantity × unit_price (GENERATED).")
    @PreAuthorize("isAuthenticated()")
    public List<InvoiceLineDto> findLinesByInvoice(@PathVariable UUID invoiceId) {
        return service.findLinesByInvoice(invoiceId);
    }

    @PostMapping("/lines")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<InvoiceLineDto> createLine(@Valid @RequestBody InvoiceLineCreateDto dto) {
        InvoiceLineDto l = service.createLine(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(l);
    }

    // ─── Wallet transactions ─────────────────────────────────────────────────

    @GetMapping("/wallet-tx")
    @Operation(summary = "Mouvements wallet paginés — filtres restaurantId / type")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public PageResponse<WalletTxDto> findAllWalletTx(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String type,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllWalletTx(restaurantId, type, page, size));
    }

    @PostMapping("/wallet-tx")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public ResponseEntity<WalletTxDto> createWalletTx(@Valid @RequestBody WalletTxCreateDto dto) {
        WalletTxDto t = service.createWalletTx(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(t);
    }

    // ─── Contract templates (V13) ────────────────────────────────────────────

    @GetMapping("/contract-templates")
    @Operation(summary = "Liste des templates contractuels — filtres tenantId / language / isActive")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<ContractTemplateDto> findAllContractTemplates(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) String language,
        @RequestParam(required = false) Boolean isActive
    ) {
        return service.findAllContractTemplates(tenantId, language, isActive);
    }

    @GetMapping("/contract-templates/{id}")
    @Operation(summary = "Détail template par UUID (admin only)")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ContractTemplateDto findContractTemplateById(@PathVariable UUID id) {
        return service.findContractTemplateById(id);
    }

    @GetMapping("/contract-templates/by-code/{code}")
    @Operation(summary = "Résolution par code (ContractDownload PDF) — fallback platform si tenant manquant")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
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
    @PreAuthorize("hasRole('SUPERADMIN')")
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
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ContractTemplateDto patchContractTemplate(
        @PathVariable UUID id, @Valid @RequestBody ContractTemplatePatchDto dto
    ) {
        return service.patchContractTemplate(id, dto);
    }

    @DeleteMapping("/contract-templates/{id}")
    @Operation(summary = "Soft delete d'un template (admin only)")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> deleteContractTemplate(@PathVariable UUID id) {
        service.softDeleteContractTemplate(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
