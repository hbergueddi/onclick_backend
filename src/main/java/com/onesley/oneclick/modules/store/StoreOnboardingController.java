package com.onesley.oneclick.modules.store;

import com.onesley.oneclick.modules.store.api.StoreOnboardingDtos.*;
import com.onesley.oneclick.modules.store.internal.StoreOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints Store onboarding — Sprint I.3.
 *
 * <p>Port EFs {@code send-onboarding-request} + {@code send-onboarding-decision}.
 *
 * <p>Workflow :
 *   1. Candidat soumet le formulaire → POST /api/store/onboarding (public)
 *   2. Admin review la demande → GET /api/store/onboarding (admin only)
 *   3. Admin décide approve/reject → PATCH /api/store/onboarding/{id}/decision
 *   4. Email branded envoyé au candidat (TODO Sprint V2 : Resend wrapper)
 */
@RestController
@RequestMapping("/api/store/onboarding")
@Tag(name = "Store onboarding", description = "Sprint I.3 — workflow demandes inscription restaurants")
public class StoreOnboardingController {

    private final StoreOnboardingService service;

    public StoreOnboardingController(StoreOnboardingService service) {
        this.service = service;
    }

    // Bug 32 (Batch D RBAC v2) — RESOURCE=TENANTS (onboarding = candidature tenant).
    @GetMapping
    @Operation(summary = "Liste des demandes (admin) — filter status optionnel")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public List<OnboardingRequestDto> findAll(@RequestParam(required = false) String status) {
        return service.findAll(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public OnboardingRequestDto findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Soumission demande inscription (public — formulaire frontend)")
    public ResponseEntity<OnboardingRequestDto> create(@Valid @RequestBody OnboardingCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PatchMapping("/{id}/decision")
    @Operation(summary = "Admin décide approve/reject la demande")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public OnboardingRequestDto decide(@PathVariable UUID id, @Valid @RequestBody OnboardingDecisionDto dto) {
        return service.decide(id, dto);
    }
}
