package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.TeamInvitationCreateDto;
import com.onesley.oneclick.modules.restaurant.api.TeamInvitationDto;
import com.onesley.oneclick.modules.restaurant.api.TeamInvitationPatchDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Service des invitations d'équipe (team_invitations).
 *
 * <p>L'ABAC par restaurant (RestaurantAccessGuard) est porté par le contrôleur ;
 * ce service expose la donnée + {@link #getRestaurantId(UUID)} pour permettre au
 * contrôleur de vérifier l'appartenance avant un PATCH.
 */
@Service
@RequiredArgsConstructor
public class TeamInvitationService {

    private final TeamInvitationRepository repository;

    @Transactional(readOnly = true)
    public List<TeamInvitationDto> listByRestaurant(UUID restaurantId) {
        return repository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
            .stream().map(TeamInvitation::toDto).toList();
    }

    /** Restaurant d'une invitation — pour le check ABAC du contrôleur (PATCH). */
    @Transactional(readOnly = true)
    public UUID getRestaurantId(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new NotFoundException("TeamInvitation", id))
            .getRestaurantId();
    }

    @Transactional
    public TeamInvitationDto create(TeamInvitationCreateDto dto) {
        TeamInvitation inv = new TeamInvitation(UUID.randomUUID(), dto.restaurantId(), dto.firstName().trim());
        inv.setInvitedBy(SecurityHelper.currentUserId());
        if (dto.lastName() != null) inv.setLastName(dto.lastName());
        if (dto.phone() != null) inv.setPhone(dto.phone());
        if (dto.role() != null) inv.setRole(dto.role());
        return repository.save(inv).toDto();
    }

    @Transactional
    public TeamInvitationDto patch(UUID id, TeamInvitationPatchDto dto) {
        TeamInvitation inv = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("TeamInvitation", id));
        if (dto.firstName() != null && !dto.firstName().isBlank()) inv.setFirstName(dto.firstName().trim());
        if (dto.lastName() != null) inv.setLastName(dto.lastName());
        if (dto.phone() != null) inv.setPhone(dto.phone());
        if (dto.role() != null) inv.setRole(dto.role());
        if (dto.status() != null) inv.setStatus(dto.status());
        return repository.save(inv).toDto();
    }
}
