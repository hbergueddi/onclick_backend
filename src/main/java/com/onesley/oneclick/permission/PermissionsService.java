package com.onesley.oneclick.permission;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service de résolution des permissions {@code app_role × menu × action}.
 *
 * <p>Stratégie :
 * <ul>
 *   <li>Charge intégralement la table {@code app_permissions} en mémoire au boot
 *       (< 200 lignes, pas de pagination)</li>
 *   <li>Structure : {@code Map<role, Set<"menu.action">>}</li>
 *   <li>Lookup O(1) — bien plus rapide qu'un SELECT par requête HTTP</li>
 *   <li>Reload via {@link #reload()} quand un admin modifie la matrice
 *       (à wirer en Phase 11+ via un endpoint admin)</li>
 * </ul>
 *
 * <p>La table {@code staff_role_permissions} (260 lignes — granularité fine
 * staff dans un restaurant) reste gérée séparément par sa propre logique
 * métier (Phase 11).
 */
@Service
public class PermissionsService {

    private static final Logger log = LoggerFactory.getLogger(PermissionsService.class);

    private final AppPermissionRepository repository;

    /**
     * Index immutable {@code role → Set<"menu.ACTION">}.
     * Construit au boot, remplacé atomiquement par {@link #reload()}.
     */
    private volatile Map<String, Set<String>> grants = Map.of();

    public PermissionsService(AppPermissionRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @Transactional(readOnly = true)
    public synchronized void reload() {
        Map<String, Set<String>> next = new HashMap<>();
        long count = 0;
        for (AppPermission p : repository.findAll()) {
            if (Boolean.FALSE.equals(p.getGranted())) continue;
            next.computeIfAbsent(p.getAppRole(), k -> new HashSet<>())
                .add(p.getMenu() + "." + p.getAction());
            count++;
        }
        // Atomic swap — old grants stay valid for in-flight requests
        this.grants = Map.copyOf(next);
        log.info("PermissionsService reloaded — {} grants across {} roles",
            count, this.grants.size());
    }

    /**
     * Vérifie si un rôle peut effectuer une action sur un menu.
     */
    public boolean can(String role, String menu, PermissionAction action) {
        Set<String> roleGrants = grants.get(role);
        if (roleGrants == null) return false;
        return roleGrants.contains(menu + "." + action.name());
    }

    /**
     * Variante prenant un {@link Authentication} — itère sur ses GrantedAuthority
     * et retourne {@code true} dès qu'un rôle a la permission.
     */
    public boolean can(Authentication auth, String menu, PermissionAction action) {
        if (auth == null || !auth.isAuthenticated()) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String authority = ga.getAuthority();
            String role = authority.startsWith("ROLE_") ? authority.substring(5) : authority;
            if (can(role, menu, action)) return true;
        }
        return false;
    }
}
