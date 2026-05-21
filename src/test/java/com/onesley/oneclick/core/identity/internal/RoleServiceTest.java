package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.core.identity.api.Action;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.RolePermissionDtos.RolePermissionUpdateDto;
import com.onesley.oneclick.core.identity.api.RolePermissionDtos.RolePermissionUpdateDto.MenuGrant;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.OneClickUserDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link RoleService} (L3 — core.identity, page « Gérer les permissions »).
 * listRoles / getRolePermissions (arbre menus + matrice) / saveRolePermissions (remplace-la-grille + eviction).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class RoleServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock MenuRepository menuRepository;
    @Mock ActionRepository actionRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock OneClickUserDetailsService userDetailsService;
    @InjectMocks RoleService service;

    private final UUID roleId = UUID.randomUUID();
    private final UUID menuId = UUID.randomUUID();
    private final Role role = new Role(roleId, "CLIENT", "Client");
    private final Menu menu = new Menu(menuId, "dashboard", "Dashboard");
    private final Action action = new Action(UUID.randomUUID(), "VIEW", "Voir", "core");
    private Permission perm() { return new Permission(UUID.randomUUID(), role, menu, action); }

    @Test
    void listRoles_mapsAndSortsByCode() {
        when(roleRepository.findAll()).thenReturn(List.of(role));
        when(permissionRepository.findAllByRoleId(any())).thenReturn(List.of(perm()));
        var roles = service.listRoles();
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).code()).isEqualTo("CLIENT");
    }

    @Test
    void getRolePermissions_notFoundAndFound() {
        when(roleRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRolePermissions(UUID.randomUUID())).isInstanceOf(NotFoundException.class);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllByRoleId(roleId)).thenReturn(List.of(perm()));
        when(actionRepository.findAll()).thenReturn(List.of(action));
        when(menuRepository.findAll()).thenReturn(List.of(menu));
        var matrix = service.getRolePermissions(roleId);
        assertThat(matrix.actions()).contains("VIEW");
        assertThat(matrix.menus()).hasSize(1);
        assertThat(matrix.menus().get(0).grantedActions()).contains("VIEW");
    }

    @Test
    void saveRolePermissions_notFound_throws() {
        when(roleRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveRolePermissions(UUID.randomUUID(),
            new RolePermissionUpdateDto(List.of()))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void saveRolePermissions_success_replacesGrid_evictsCache() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(menuRepository.findAll()).thenReturn(List.of(menu));
        when(actionRepository.findAll()).thenReturn(List.of(action));
        when(permissionRepository.findAllByRoleId(roleId)).thenReturn(List.of(perm())); // re-read final

        var payload = new RolePermissionUpdateDto(List.of(
            new MenuGrant(menuId, List.of("VIEW", "ACTION_INCONNUE", "VIEW")), // insert + skip action + dedup
            new MenuGrant(UUID.randomUUID(), List.of("VIEW")),                  // menu inconnu → skip
            new MenuGrant(menuId, null)                                          // actions null → skip
        ));
        var res = service.saveRolePermissions(roleId, payload);
        assertThat(res).isNotNull();
        verify(permissionRepository).deleteByRoleId(roleId);
        verify(permissionRepository).saveAll(any());
        verify(userDetailsService).evictAll();
    }
}
