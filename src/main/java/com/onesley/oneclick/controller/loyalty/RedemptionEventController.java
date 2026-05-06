package com.onesley.oneclick.controller.loyalty;

import com.onesley.oneclick.dto.loyalty.RedemptionEventDto;
import com.onesley.oneclick.service.loyalty.RedemptionEventService;
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
 * REST controller pour {@link RedemptionEventDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/redemption-events")
@Tag(name = "RedemptionEvent", description = "Auto-generated controller for redemption_events")
public class RedemptionEventController {

    private final RedemptionEventService service;

    public RedemptionEventController(RedemptionEventService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<RedemptionEventDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une RedemptionEvent par UUID")
    public ResponseEntity<RedemptionEventDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
