package com.onesley.oneclick.modules.financial;

import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.financial.FinancialDtos.*;

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
    public PageResponse<ContractDto> findAllContracts(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllContracts(restaurantId, status, page, size));
    }

    @GetMapping("/contracts/{id}")
    public ContractDto findContractById(@PathVariable UUID id) { return service.findContractById(id); }

    @PostMapping("/contracts")
    public ResponseEntity<ContractDto> createContract(@Valid @RequestBody ContractCreateDto dto) {
        ContractDto c = service.createContract(dto);
        return ResponseEntity.created(URI.create("/api/financial/contracts/" + c.id())).body(c);
    }

    @PatchMapping("/contracts/{id}")
    public ContractDto updateContract(@PathVariable UUID id, @Valid @RequestBody ContractUpdateDto dto) {
        return service.updateContract(id, dto);
    }

    // ─── Invoices ────────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    @Operation(summary = "Factures paginées — filtres restaurantId / status")
    public PageResponse<InvoiceDto> findAllInvoices(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllInvoices(restaurantId, status, page, size));
    }

    @GetMapping("/invoices/{id}")
    public InvoiceDto findInvoiceById(@PathVariable UUID id) { return service.findInvoiceById(id); }

    @PostMapping("/invoices")
    public ResponseEntity<InvoiceDto> createInvoice(@Valid @RequestBody InvoiceCreateDto dto) {
        InvoiceDto i = service.createInvoice(dto);
        return ResponseEntity.created(URI.create("/api/financial/invoices/" + i.id())).body(i);
    }

    @PatchMapping("/invoices/{id}")
    @Operation(summary = "Mise à jour totaux / status. status=paid → paid_at automatique.")
    public InvoiceDto updateInvoice(@PathVariable UUID id, @Valid @RequestBody InvoiceUpdateDto dto) {
        return service.updateInvoice(id, dto);
    }

    // ─── Invoice lines ───────────────────────────────────────────────────────

    @GetMapping("/invoices/{invoiceId}/lines")
    @Operation(summary = "Lignes d'une facture — line_total = quantity × unit_price (GENERATED).")
    public List<InvoiceLineDto> findLinesByInvoice(@PathVariable UUID invoiceId) {
        return service.findLinesByInvoice(invoiceId);
    }

    @PostMapping("/lines")
    public ResponseEntity<InvoiceLineDto> createLine(@Valid @RequestBody InvoiceLineCreateDto dto) {
        InvoiceLineDto l = service.createLine(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(l);
    }

    // ─── Wallet transactions ─────────────────────────────────────────────────

    @GetMapping("/wallet-tx")
    @Operation(summary = "Mouvements wallet paginés — filtres restaurantId / type")
    public PageResponse<WalletTxDto> findAllWalletTx(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String type,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllWalletTx(restaurantId, type, page, size));
    }

    @PostMapping("/wallet-tx")
    public ResponseEntity<WalletTxDto> createWalletTx(@Valid @RequestBody WalletTxCreateDto dto) {
        WalletTxDto t = service.createWalletTx(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(t);
    }
}
