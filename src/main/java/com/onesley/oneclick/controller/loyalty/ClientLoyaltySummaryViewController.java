package com.onesley.oneclick.controller.loyalty;

import com.onesley.oneclick.dto.loyalty.ClientLoyaltySummaryViewDto;
import com.onesley.oneclick.service.loyalty.ClientLoyaltySummaryViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link ClientLoyaltySummaryViewDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/views/client-loyalty-summarys")
@Tag(name = "ClientLoyaltySummaryView", description = "Auto-generated controller for v_client_loyalty_summary")
public class ClientLoyaltySummaryViewController {

    private final ClientLoyaltySummaryViewService service;

    public ClientLoyaltySummaryViewController(ClientLoyaltySummaryViewService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<ClientLoyaltySummaryViewDto> findAll() {
        return service.findAll();
    }
}
