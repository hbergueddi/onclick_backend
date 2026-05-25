package com.onesley.oneclick.modules.social;

import com.onesley.oneclick.modules.social.api.SocialExtensionDtos.*;
import com.onesley.oneclick.modules.social.internal.SocialExtensionService;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Bug 32 (Batch D RBAC v2) — RESOURCE COMMUNITY pour Elite apps,
 * RESTAURANTS pour Restaurant groups (Galaxy = grouping de restos).
 */
@RestController
@Tag(name = "Social extensions", description = "Sprint H — Elite applications + Restaurant groups (Galaxy)")
@RequiredArgsConstructor
public class SocialExtensionController {

    private final SocialExtensionService service;

    // ─── Elite applications ─────────────────────────────────────────────
    @GetMapping("/api/social/elite-applications")
    @Operation(summary = "Liste des demandes Elite (admin Forge)")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<EliteApplicationDto> findAll(@RequestParam(required = false) String status) {
        // Vue admin (Forge) : un non-admin passe par /by-user/{self} pour ses demandes.
        if (!SecurityHelper.isAdmin()) {
            throw new ForbiddenException("Liste des demandes Elite réservée à l'administration");
        }
        return status != null ? service.findApplicationsByStatus(status) : service.findAllApplications();
    }

    @GetMapping("/api/social/elite-applications/by-user/{userId}")
    @Operation(summary = "Demandes Elite d'un user (Pocket profile)")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<EliteApplicationDto> findByUser(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId); // ses propres demandes Elite (ou admin)
        return service.findUserApplications(userId);
    }

    @PostMapping("/api/social/elite-applications")
    @Operation(summary = "Soumet une demande Elite")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ResponseEntity<EliteApplicationDto> create(@Valid @RequestBody EliteApplicationCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createApplication(dto));
    }

    @PatchMapping("/api/social/elite-applications/{id}/review")
    @Operation(summary = "Approuve/refuse une demande Elite (admin)")
    @PreAuthorize("hasAuthority('UPDATE:COMMUNITY')")
    public EliteApplicationDto review(@PathVariable UUID id, @Valid @RequestBody EliteApplicationReviewDto dto) {
        return service.reviewApplication(id, dto);
    }

    // ─── Restaurant groups (Galaxy concept) ─────────────────────────────
    @GetMapping("/api/restaurant-groups")
    @Operation(summary = "Liste des groupes de restaurants")
    @PreAuthorize("hasAuthority('VIEW:RESTAURANTS')")
    public List<RestaurantGroupDto> findAllGroups(@RequestParam(required = false) UUID ownerId) {
        return ownerId != null ? service.findOwnerGroups(ownerId) : service.findAllGroups();
    }

    @GetMapping("/api/restaurant-groups/{id}")
    @PreAuthorize("hasAuthority('VIEW:RESTAURANTS')")
    public RestaurantGroupDto findGroup(@PathVariable UUID id) {
        return service.findGroup(id);
    }

    @PostMapping("/api/restaurant-groups")
    @PreAuthorize("hasAuthority('CREATE:RESTAURANTS')")
    public ResponseEntity<RestaurantGroupDto> createGroup(@Valid @RequestBody RestaurantGroupCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGroup(dto));
    }

    @PatchMapping("/api/restaurant-groups/{id}")
    @PreAuthorize("hasAuthority('UPDATE:RESTAURANTS')")
    public RestaurantGroupDto updateGroup(@PathVariable UUID id, @Valid @RequestBody RestaurantGroupCreateDto dto) {
        return service.updateGroup(id, dto);
    }

    @DeleteMapping("/api/restaurant-groups/{id}")
    @PreAuthorize("hasAuthority('DELETE:RESTAURANTS')")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        service.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
}
