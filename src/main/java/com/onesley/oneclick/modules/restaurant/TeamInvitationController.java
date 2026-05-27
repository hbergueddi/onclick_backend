package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.modules.restaurant.api.TeamInvitationCreateDto;
import com.onesley.oneclick.modules.restaurant.api.TeamInvitationDto;
import com.onesley.oneclick.modules.restaurant.api.TeamInvitationPatchDto;
import com.onesley.oneclick.modules.restaurant.internal.TeamInvitationService;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * REST controller {@code /api/team-invitations} — invitations d'équipe (pending staff).
 *
 * <p>RBAC v2 senior strict : autorité STAFF ({@code hasAuthority('VERB:STAFF')},
 * jamais isAuthenticated()/hasRole()) + ABAC par restaurant via
 * {@link RestaurantAccessGuard#requireAdminOrActiveStaffOf} (un gérant ne gère que
 * les invitations de SES restaurants ; admin/group-admin passent).
 */
@RestController
@RequestMapping("/api/team-invitations")
@Tag(name = "Team Invitations", description = "Invitations d'équipe (pending staff)")
@RequiredArgsConstructor
public class TeamInvitationController {

    private final TeamInvitationService service;
    private final RestaurantAccessGuard restaurantAccessGuard;

    @GetMapping("/by-restaurant/{restaurantId}")
    @Operation(summary = "Invitations d'un restaurant")
    @PreAuthorize("hasAuthority('VIEW:STAFF')")
    public List<TeamInvitationDto> byRestaurant(@PathVariable UUID restaurantId) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.listByRestaurant(restaurantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crée une invitation d'équipe")
    @PreAuthorize("hasAuthority('CREATE:STAFF')")
    public TeamInvitationDto create(@Valid @RequestBody TeamInvitationCreateDto dto) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(dto.restaurantId());
        return service.create(dto);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Met à jour une invitation (profil / statut accepted|disabled)")
    @PreAuthorize("hasAuthority('UPDATE:STAFF')")
    public TeamInvitationDto patch(@PathVariable UUID id, @Valid @RequestBody TeamInvitationPatchDto dto) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(service.getRestaurantId(id));
        return service.patch(id, dto);
    }
}
