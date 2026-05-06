package com.onesley.oneclick.controller.admin;

import com.onesley.oneclick.dto.admin.AdminWalletTransactionDto;
import com.onesley.oneclick.service.admin.AdminWalletTransactionService;
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
 * REST controller pour {@link AdminWalletTransactionDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/admin-wallet-transactions")
@Tag(name = "AdminWalletTransaction", description = "Auto-generated controller for admin_wallet_transactions")
public class AdminWalletTransactionController {

    private final AdminWalletTransactionService service;

    public AdminWalletTransactionController(AdminWalletTransactionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<AdminWalletTransactionDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une AdminWalletTransaction par UUID")
    public ResponseEntity<AdminWalletTransactionDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
