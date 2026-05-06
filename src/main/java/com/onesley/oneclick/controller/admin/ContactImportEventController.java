package com.onesley.oneclick.controller.admin;

import com.onesley.oneclick.dto.admin.ContactImportEventDto;
import com.onesley.oneclick.service.admin.ContactImportEventService;
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
 * REST controller pour {@link ContactImportEventDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/contact-import-events")
@Tag(name = "ContactImportEvent", description = "Auto-generated controller for contact_import_events")
public class ContactImportEventController {

    private final ContactImportEventService service;

    public ContactImportEventController(ContactImportEventService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<ContactImportEventDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une ContactImportEvent par UUID")
    public ResponseEntity<ContactImportEventDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
