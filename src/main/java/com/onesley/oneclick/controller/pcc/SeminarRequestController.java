package com.onesley.oneclick.controller.pcc;

import com.onesley.oneclick.dto.pcc.SeminarRequestDto;
import com.onesley.oneclick.service.pcc.SeminarRequestService;
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
 * REST controller pour {@link SeminarRequestDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/seminar-requests")
@Tag(name = "SeminarRequest", description = "Auto-generated controller for seminar_requests")
public class SeminarRequestController {

    private final SeminarRequestService service;

    public SeminarRequestController(SeminarRequestService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<SeminarRequestDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une SeminarRequest par UUID")
    public ResponseEntity<SeminarRequestDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
