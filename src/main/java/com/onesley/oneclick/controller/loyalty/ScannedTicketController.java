package com.onesley.oneclick.controller.loyalty;

import com.onesley.oneclick.dto.loyalty.ScannedTicketDto;
import com.onesley.oneclick.service.loyalty.ScannedTicketService;
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
 * REST controller pour {@link ScannedTicketDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/scanned-tickets")
@Tag(name = "ScannedTicket", description = "Auto-generated controller for scanned_tickets")
public class ScannedTicketController {

    private final ScannedTicketService service;

    public ScannedTicketController(ScannedTicketService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<ScannedTicketDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une ScannedTicket par UUID")
    public ResponseEntity<ScannedTicketDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
