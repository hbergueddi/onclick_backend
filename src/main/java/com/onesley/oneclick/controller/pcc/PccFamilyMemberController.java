package com.onesley.oneclick.controller.pcc;

import com.onesley.oneclick.dto.pcc.PccFamilyMemberDto;
import com.onesley.oneclick.service.pcc.PccFamilyMemberService;
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
 * REST controller pour {@link PccFamilyMemberDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/pcc-family-members")
@Tag(name = "PccFamilyMember", description = "Auto-generated controller for pcc_family_members")
public class PccFamilyMemberController {

    private final PccFamilyMemberService service;

    public PccFamilyMemberController(PccFamilyMemberService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<PccFamilyMemberDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une PccFamilyMember par UUID")
    public ResponseEntity<PccFamilyMemberDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
