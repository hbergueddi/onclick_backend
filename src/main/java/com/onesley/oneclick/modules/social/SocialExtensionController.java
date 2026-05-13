package com.onesley.oneclick.modules.social;

import com.onesley.oneclick.modules.social.api.SocialExtensionDtos.*;
import com.onesley.oneclick.modules.social.internal.SocialExtensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Social extensions", description = "Sprint H — Elite applications + Restaurant groups (Galaxy)")
public class SocialExtensionController {

    private final SocialExtensionService service;

    public SocialExtensionController(SocialExtensionService service) {
        this.service = service;
    }

    // ─── Elite applications ─────────────────────────────────────────────
    @GetMapping("/api/social/elite-applications")
    @Operation(summary = "Liste des demandes Elite (admin Forge)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public List<EliteApplicationDto> findAll(@RequestParam(required = false) String status) {
        return status != null ? service.findApplicationsByStatus(status) : service.findAllApplications();
    }

    @GetMapping("/api/social/elite-applications/by-user/{userId}")
    @Operation(summary = "Demandes Elite d'un user (Pocket profile)")
    @PreAuthorize("isAuthenticated()")
    public List<EliteApplicationDto> findByUser(@PathVariable UUID userId) {
        return service.findUserApplications(userId);
    }

    @PostMapping("/api/social/elite-applications")
    @Operation(summary = "Soumet une demande Elite")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EliteApplicationDto> create(@Valid @RequestBody EliteApplicationCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createApplication(dto));
    }

    @PatchMapping("/api/social/elite-applications/{id}/review")
    @Operation(summary = "Approuve/refuse une demande Elite (admin)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public EliteApplicationDto review(@PathVariable UUID id, @Valid @RequestBody EliteApplicationReviewDto dto) {
        return service.reviewApplication(id, dto);
    }

    // ─── Restaurant groups (Galaxy concept) ─────────────────────────────
    @GetMapping("/api/restaurant-groups")
    @Operation(summary = "Liste des groupes de restaurants")
    @PreAuthorize("isAuthenticated()")
    public List<RestaurantGroupDto> findAllGroups(@RequestParam(required = false) UUID ownerId) {
        return ownerId != null ? service.findOwnerGroups(ownerId) : service.findAllGroups();
    }

    @GetMapping("/api/restaurant-groups/{id}")
    @PreAuthorize("isAuthenticated()")
    public RestaurantGroupDto findGroup(@PathVariable UUID id) {
        return service.findGroup(id);
    }

    @PostMapping("/api/restaurant-groups")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public ResponseEntity<RestaurantGroupDto> createGroup(@Valid @RequestBody RestaurantGroupCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGroup(dto));
    }

    @PatchMapping("/api/restaurant-groups/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public RestaurantGroupDto updateGroup(@PathVariable UUID id, @Valid @RequestBody RestaurantGroupCreateDto dto) {
        return service.updateGroup(id, dto);
    }

    @DeleteMapping("/api/restaurant-groups/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        service.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
}
