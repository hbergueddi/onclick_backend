package com.onesley.oneclick.controller.auth;

import com.onesley.oneclick.dto.auth.ProfileDto;
import com.onesley.oneclick.dto.auth.ProfileUpdateDto;
import com.onesley.oneclick.service.auth.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@Tag(name = "Profiles", description = "Profil applicatif d'un utilisateur (1-1 avec auth.users)")
// TODO Phase 11 : raffiner — un client ne peut accéder qu'à son propre profil
// (ex: @PostAuthorize("returnObject.id.toString() == authentication.name") sur findById,
// + @PreAuthorize au niveau méthode pour patch/delete avec check id==sub)
@PreAuthorize("hasAnyRole('admin','client')")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupère un profil par son UUID")
    public ResponseEntity<ProfileDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-email")
    @Operation(summary = "Recherche un profil par son email")
    public ResponseEntity<ProfileDto> findByEmail(@RequestParam String email) {
        return service.findByEmail(email)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-referral")
    @Operation(summary = "Recherche un profil par son code de parrainage")
    public ResponseEntity<ProfileDto> findByReferral(@RequestParam String code) {
        return service.findByReferralCode(code)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Liste les profils d'un tenant (whitelabel multi-tenant)")
    public List<ProfileDto> findByTenant(@RequestParam UUID tenantId) {
        return service.findByTenant(tenantId);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Mise à jour partielle d'un profil (PATCH sémantique)")
    public ProfileDto patch(@PathVariable UUID id, @Valid @RequestBody ProfileUpdateDto dto) {
        return service.patch(id, dto);
    }
}
