package com.onesley.oneclick.etl.steps;

import com.onesley.oneclick.etl.EtlStep;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 2 — Roles canoniques.
 *
 * <p>Crée les 5 rôles enterprise (mapping depuis l'enum legacy {@code user_roles.role}) :
 * <ul>
 *   <li>{@code CLIENT}       — défaut, parcours utilisateur final</li>
 *   <li>{@code STAFF}        — staff restaurant (serveur, manager, etc.)</li>
 *   <li>{@code RESTAURATEUR} — owner restaurant</li>
 *   <li>{@code GROUP_ADMIN}  — admin groupe restaurants ou tenant whitelabel</li>
 *   <li>{@code SUPERADMIN}   — admin plateforme (équipe OneClick)</li>
 * </ul>
 *
 * <p>Les rôles ont des UUIDs déterministes pour pouvoir y faire référence depuis
 * UsersStep sans table lookup.
 *
 * <p>NB Phase 13.A : on ne crée PAS encore les menus/actions/permissions (le système
 * RBAC enterprise est plus simple que legacy — la reconstruction se fera plus tard
 * via un workflow admin dédié).
 */
@Component
@Profile("etl")
public class RolesStep extends EtlStep.AbstractEtlStep {

    // UUIDs déterministes pour les rôles canoniques (référencés par UsersStep)
    public static final String ROLE_CLIENT       = "10000000-0000-0000-0000-000000000001";
    public static final String ROLE_STAFF        = "10000000-0000-0000-0000-000000000002";
    public static final String ROLE_RESTAURATEUR = "10000000-0000-0000-0000-000000000003";
    public static final String ROLE_GROUP_ADMIN  = "10000000-0000-0000-0000-000000000004";
    public static final String ROLE_SUPERADMIN   = "10000000-0000-0000-0000-000000000005";

    public RolesStep(JdbcTemplate jdbc, TransactionTemplate tx) {
        super(jdbc, tx);
    }

    @Override public String getName() { return "roles"; }

    @Override
    public String[] getTargetTables() {
        // Reverse FK order : permissions → menus + actions → roles
        return new String[] { "permissions", "menus", "actions", "roles" };
    }

    @Override
    public long migrate() {
        // 5 rôles canoniques avec UUIDs déterministes
        long inserted = 0;
        inserted += jdbc.update(
            "INSERT INTO roles (id, code, name, created_at, updated_at) VALUES (?::uuid, ?, ?, now(), now())",
            ROLE_CLIENT, "CLIENT", "Client final");
        inserted += jdbc.update(
            "INSERT INTO roles (id, code, name, created_at, updated_at) VALUES (?::uuid, ?, ?, now(), now())",
            ROLE_STAFF, "STAFF", "Staff restaurant");
        inserted += jdbc.update(
            "INSERT INTO roles (id, code, name, created_at, updated_at) VALUES (?::uuid, ?, ?, now(), now())",
            ROLE_RESTAURATEUR, "RESTAURATEUR", "Restaurateur (owner)");
        inserted += jdbc.update(
            "INSERT INTO roles (id, code, name, created_at, updated_at) VALUES (?::uuid, ?, ?, now(), now())",
            ROLE_GROUP_ADMIN, "GROUP_ADMIN", "Admin groupe / tenant");
        inserted += jdbc.update(
            "INSERT INTO roles (id, code, name, created_at, updated_at) VALUES (?::uuid, ?, ?, now(), now())",
            ROLE_SUPERADMIN, "SUPERADMIN", "Admin plateforme OneClick");

        log.debug("  roles : {} rows", inserted);
        return inserted;
    }

    @Override
    public void validate() {
        long n = countTarget("roles");
        if (n != 5) {
            throw new IllegalStateException("roles : attendu 5, trouvé " + n);
        }
    }
}
