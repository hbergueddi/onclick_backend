package com.onesley.oneclick.controller.contract;

import com.onesley.oneclick.dto.contract.ContractDisabledArticleDto;
import com.onesley.oneclick.service.contract.ContractDisabledArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link ContractDisabledArticleDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasRole('admin').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/contract-disabled-articles")
@Tag(name = "ContractDisabledArticle", description = "Auto-generated controller for contract_disabled_articles")
@PreAuthorize("hasRole('admin')")
public class ContractDisabledArticleController {

    private final ContractDisabledArticleService service;

    public ContractDisabledArticleController(ContractDisabledArticleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<ContractDisabledArticleDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une ContractDisabledArticle par UUID")
    public ResponseEntity<ContractDisabledArticleDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
