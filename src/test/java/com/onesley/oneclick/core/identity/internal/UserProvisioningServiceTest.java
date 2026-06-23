package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserCreateDto;
import com.onesley.oneclick.core.identity.api.UserDto;
import com.onesley.oneclick.core.identity.api.UserProvisioningApi.ProvisionOwnerCommand;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés de {@link UserProvisioningService} (BE-2, core.identity).
 *
 * <p>Vérifie la résolution du rôle {@code RESTAURATEUR}, la délégation à {@code UserService.create}
 * (avec les bons champs), et le positionnement du drapeau {@code password_must_change=true} (BE-3).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class UserProvisioningServiceTest {

    @Mock UserService userService;
    @Mock RoleRepository roleRepository;
    @Mock UserRepository userRepository;
    @InjectMocks UserProvisioningService service;

    private final Role restaurateur = new Role(UUID.randomUUID(), "RESTAURATEUR", "Restaurateur");

    private ProvisionOwnerCommand cmd() {
        return new ProvisionOwnerCommand(UUID.randomUUID(), "owner@x.ma", "Ada", "Lovelace", "+212600", "TempPass2345");
    }

    @Test
    void provisionOwner_createsRestaurateur_withTempPassword_setsMustChange() {
        UUID newId = UUID.randomUUID();
        User created = new User(newId, restaurateur, "owner@x.ma", "$2a$h", "Ada", "Lovelace");
        UserDto dto = mock(UserDto.class);
        when(dto.id()).thenReturn(newId);
        when(roleRepository.findByCode("RESTAURATEUR")).thenReturn(Optional.of(restaurateur));
        when(userService.create(any())).thenReturn(dto);
        when(userRepository.findById(newId)).thenReturn(Optional.of(created));

        UUID result = service.provisionOwner(cmd());

        assertThat(result).isEqualTo(newId);
        // create() reçoit le rôle RESTAURATEUR + le mdp temporaire + les coordonnées.
        ArgumentCaptor<UserCreateDto> cap = ArgumentCaptor.forClass(UserCreateDto.class);
        verify(userService).create(cap.capture());
        assertThat(cap.getValue().roleId()).isEqualTo(restaurateur.getId());
        assertThat(cap.getValue().email()).isEqualTo("owner@x.ma");
        assertThat(cap.getValue().password()).isEqualTo("TempPass2345");
        // BE-3 — le drapeau « changement forcé » est posé puis persisté.
        assertThat(created.isPasswordMustChange()).isTrue();
        verify(userRepository).save(created);
    }

    @Test
    void provisionOwner_roleMissing_throws_noCreate() {
        when(roleRepository.findByCode("RESTAURATEUR")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.provisionOwner(cmd())).isInstanceOf(NotFoundException.class);
        verify(userService, never()).create(any());
    }
}
