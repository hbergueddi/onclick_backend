package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.TeamInvitationCreateDto;
import com.onesley.oneclick.modules.restaurant.api.TeamInvitationPatchDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link TeamInvitationService} (invitations, V47).
 * Repo mocké. {@code SecurityHelper.currentUserId()} renvoie null hors contexte
 * (invitedBy null) — pas de mock statique nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class TeamInvitationServiceTest {

    @Mock TeamInvitationRepository repo;
    @InjectMocks TeamInvitationService service;

    @Test
    void listByRestaurant_maps() {
        UUID rid = UUID.randomUUID();
        TeamInvitation inv = new TeamInvitation(UUID.randomUUID(), rid, "Karim");
        when(repo.findByRestaurantIdOrderByCreatedAtDesc(rid)).thenReturn(List.of(inv));
        var dtos = service.listByRestaurant(rid);
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).firstName()).isEqualTo("Karim");
        assertThat(dtos.get(0).status()).isEqualTo("pending");
    }

    @Test
    void getRestaurantId_notFound_throws() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRestaurantId(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getRestaurantId_returnsRestaurant() {
        UUID rid = UUID.randomUUID();
        TeamInvitation inv = new TeamInvitation(UUID.randomUUID(), rid, "K");
        when(repo.findById(inv.getId())).thenReturn(Optional.of(inv));
        assertThat(service.getRestaurantId(inv.getId())).isEqualTo(rid);
    }

    @Test
    void create_setsDefaultsAndFields() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.create(new TeamInvitationCreateDto(
            UUID.randomUUID(), "Alice", "Martin", "+212600000001", "manager"));
        assertThat(dto.firstName()).isEqualTo("Alice");
        assertThat(dto.role()).isEqualTo("manager");
        assertThat(dto.status()).isEqualTo("pending");
    }

    @Test
    void patch_notFound_throws() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(),
            new TeamInvitationPatchDto(null, null, null, null, "disabled")))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patch_appliesStatus() {
        TeamInvitation inv = new TeamInvitation(UUID.randomUUID(), UUID.randomUUID(), "K");
        when(repo.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.patch(inv.getId(), new TeamInvitationPatchDto(null, null, null, null, "disabled"));
        assertThat(dto.status()).isEqualTo("disabled");
    }
}
