package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.core.identity.api.Action;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.RolePermissionDtos.RolePermissionUpdateDto.MenuGrant;
import com.onesley.oneclick.core.identity.api.RolePermissionDtos.RolePermissionMatrixDto;
import com.onesley.oneclick.core.identity.api.RolePermissionDtos.RolePermissionMatrixDto.MenuNode;
import com.onesley.oneclick.core.identity.api.RolePermissionDtos.RolePermissionUpdateDto;
import com.onesley.oneclick.core.identity.api.RolePermissionDtos.RoleSummaryDto;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.OneClickUserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Service de gestion RBAC (page « Gérer les permissions »).
 *
 * <p>Domaine identity : sert l'arbre des menus + la matrice des verbes cochés
 * d'un rôle, et enregistre la grille (remplace toutes les permissions du rôle).
 * Après sauvegarde, évince le cache {@code userDetails} (structure RBAC modifiée).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoleService {

    /** Ordre d'affichage des colonnes (aligné sur l'UI). */
    private static final List<String> ACTION_ORDER =
        List.of("CREATE", "VIEW", "UPDATE", "DELETE", "UPLOAD", "DOWNLOAD");

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final ActionRepository actionRepository;
    private final PermissionRepository permissionRepository;
    private final OneClickUserDetailsService userDetailsService;

    public List<RoleSummaryDto> listRoles() {
        return roleRepository.findAll().stream()
            .map(r -> new RoleSummaryDto(r.getId(), r.getCode(), r.getName(),
                permissionRepository.findAllByRoleId(r.getId()).size()))
            .sorted(Comparator.comparing(RoleSummaryDto::code))
            .toList();
    }

    public RolePermissionMatrixDto getRolePermissions(UUID roleId) {
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new NotFoundException("Role", roleId));

        // menuId -> verbes accordés au rôle
        Map<UUID, Set<String>> granted = new HashMap<>();
        for (Permission p : permissionRepository.findAllByRoleId(roleId)) {
            if (p.getMenu() != null && p.getAction() != null) {
                granted.computeIfAbsent(p.getMenu().getId(), k -> new HashSet<>())
                    .add(p.getAction().getCode());
            }
        }

        // ordre des actions = ACTION_ORDER restreint à celles existant en base
        Set<String> dbActions = new HashSet<>();
        actionRepository.findAll().forEach(a -> dbActions.add(a.getCode()));
        List<String> actions = ACTION_ORDER.stream().filter(dbActions::contains).toList();

        List<Menu> all = menuRepository.findAll();
        return new RolePermissionMatrixDto(
            role.getId(), role.getCode(), role.getName(), actions,
            buildTree(null, all, granted, actions));
    }

    /** Construit récursivement l'arbre des menus sous {@code parentId}. */
    private List<MenuNode> buildTree(UUID parentId, List<Menu> all,
                                     Map<UUID, Set<String>> granted, List<String> actions) {
        return all.stream()
            .filter(m -> java.util.Objects.equals(m.getParentId(), parentId))
            .sorted(Comparator.comparing(m -> m.getSortOrder() == null ? 0 : m.getSortOrder()))
            .map(m -> {
                Set<String> g = granted.getOrDefault(m.getId(), Set.of());
                List<String> ordered = actions.stream().filter(g::contains).toList();
                return new MenuNode(m.getId(), m.getCode(), m.getName(), m.getIcon(), m.getPath(),
                    m.getSortOrder(), ordered, buildTree(m.getId(), all, granted, actions));
            })
            .toList();
    }

    @Transactional
    public RolePermissionMatrixDto saveRolePermissions(UUID roleId, RolePermissionUpdateDto payload) {
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new NotFoundException("Role", roleId));

        Map<UUID, Menu> menuById = new HashMap<>();
        menuRepository.findAll().forEach(m -> menuById.put(m.getId(), m));
        Map<String, Action> actionByCode = new HashMap<>();
        actionRepository.findAll().forEach(a -> actionByCode.put(a.getCode(), a));

        // Remplace-la-grille : on efface tout puis on ré-insère la sélection.
        permissionRepository.deleteByRoleId(roleId);
        permissionRepository.flush();

        List<Permission> toInsert = new ArrayList<>();
        Set<String> dedup = new HashSet<>();
        if (payload.menus() != null) {
            for (MenuGrant grant : payload.menus()) {
                Menu menu = menuById.get(grant.menuId());
                if (menu == null || grant.actions() == null) continue;
                for (String code : grant.actions()) {
                    Action action = actionByCode.get(code);
                    if (action == null) continue;
                    if (!dedup.add(menu.getId() + ":" + code)) continue; // anti-doublon
                    toInsert.add(new Permission(UUID.randomUUID(), role, menu, action));
                }
            }
        }
        permissionRepository.saveAll(toInsert);

        // La structure RBAC a changé pour (potentiellement) plusieurs users du rôle.
        userDetailsService.evictAll();

        return getRolePermissions(roleId);
    }
}
