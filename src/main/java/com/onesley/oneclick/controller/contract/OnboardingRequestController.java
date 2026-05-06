package com.onesley.oneclick.controller.contract;

import com.onesley.oneclick.dto.contract.OnboardingRequestDto;
import com.onesley.oneclick.service.contract.OnboardingRequestService;
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
 * REST controller pour {@link OnboardingRequestDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/onboarding-requests")
@Tag(name = "OnboardingRequest", description = "Auto-generated controller for onboarding_requests")
public class OnboardingRequestController {

    private final OnboardingRequestService service;

    public OnboardingRequestController(OnboardingRequestService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<OnboardingRequestDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une OnboardingRequest par UUID")
    public ResponseEntity<OnboardingRequestDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
