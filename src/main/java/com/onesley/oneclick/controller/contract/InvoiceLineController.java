package com.onesley.oneclick.controller.contract;

import com.onesley.oneclick.dto.contract.InvoiceLineDto;
import com.onesley.oneclick.service.contract.InvoiceLineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link InvoiceLineDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/invoice-lines")
@Tag(name = "InvoiceLine", description = "Auto-generated controller for invoice_lines")
public class InvoiceLineController {

    private final InvoiceLineService service;

    public InvoiceLineController(InvoiceLineService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<InvoiceLineDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une InvoiceLine par UUID")
    public ResponseEntity<InvoiceLineDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
