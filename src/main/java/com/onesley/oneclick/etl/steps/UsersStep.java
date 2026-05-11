package com.onesley.oneclick.etl.steps;

import com.onesley.oneclick.etl.EtlProperties;
import com.onesley.oneclick.etl.EtlStep;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 3 — Users (17221 rows) — fusion {@code profiles} + {@code user_roles} + {@code auth.users}.
 *
 * <p>Transformations clés :
 * <ul>
 *   <li><b>Email</b> : depuis {@code auth.users.email} (présent dans 100% des cas)
 *       avec fallback sur {@code profiles.email} (peut être null)</li>
 *   <li><b>Password hash</b> : depuis {@code auth.users.encrypted_password} (BCrypt {@code $2a$10$...},
 *       100% compatible Spring {@link BCryptPasswordEncoder}). Les 7 users sans hash legacy
 *       reçoivent un BCrypt de {@code etl.fallback-password} (cf décision #2 validée).</li>
 *   <li><b>Role</b> : 1:1 vu qu'aucun user n'a plusieurs rôles en legacy (vérifié audit).
 *       Mapping enum {@code user_roles.role} → uuid canonique via {@link RolesStep}.</li>
 *   <li><b>Tenant</b> : {@code profiles.tenant_id} si présent, sinon NULL (admin platform).</li>
 * </ul>
 *
 * <p>Stratégie de perf : 1 seul INSERT...SELECT (PG-side join + role lookup) pour les 17000+
 * rows en mode "preserve hashs". Le fallback BCrypt est calculé en Java AVANT le SELECT massif,
 * puis injecté comme paramètre constant (pas un appel BCrypt par row).
 */
@Component
@Profile("etl")
public class UsersStep extends EtlStep.AbstractEtlStep {

    private final EtlProperties props;
    private final BCryptPasswordEncoder bcrypt;

    public UsersStep(JdbcTemplate jdbc, TransactionTemplate tx, EtlProperties props) {
        super(jdbc, tx);
        this.props = props;
        this.bcrypt = new BCryptPasswordEncoder(12);
    }

    @Override public String getName() { return "users"; }

    @Override
    public String[] getTargetTables() {
        return new String[] { "users" };
    }

    @Override
    public long migrate() {
        // BCrypt du fallback password (1x — pas par row !)
        String fallbackHash = bcrypt.encode(props.getFallbackPassword());
        log.debug("  fallback hash (length={}): {}...", fallbackHash.length(), fallbackHash.substring(0, 7));

        // ─── INSERT massif : 1 seul SQL pour les 17k+ rows ─────────────────────
        // Mapping enum legacy → role_id enterprise via CASE WHEN
        // Source : profiles (1:1 avec auth.users) + user_roles (1:1 par user)
        //
        // Email : COALESCE(auth.users.email, profiles.email, fallback synthétique)
        // Hash  : COALESCE(auth.users.encrypted_password, fallback BCrypt)
        // Phone : déduplication via ROW_NUMBER — on garde 1 occurrence par phone (la 1ère
        //         par created_at), les doublons reçoivent NULL pour respecter UNIQUE(phone).
        //         Vu audit : 5 phones doublons en legacy (seed staff probablement).
        long inserted = jdbc.update("""
            WITH ranked AS (
              SELECT
                p.id, p.tenant_id, p.first_name, p.last_name, p.avatar_url,
                p.language, p.email AS profile_email,
                p.phone,
                p.created_at, p.updated_at,
                ur.role,
                au.email AS auth_email,
                au.encrypted_password,
                ROW_NUMBER() OVER (
                  PARTITION BY p.phone
                  ORDER BY p.created_at NULLS LAST, p.id
                ) AS phone_rank
              FROM legacy.profiles p
              LEFT JOIN legacy.user_roles ur ON ur.user_id = p.id
              LEFT JOIN legacy_auth.users au ON au.id = p.id
            )
            INSERT INTO users (
              id, tenant_id, role_id, email, phone, password_hash,
              first_name, last_name, avatar_url, language, status,
              account_non_expired, account_non_locked, credentials_non_expired, enabled,
              created_at, updated_at
            )
            SELECT
              id,
              tenant_id,
              CASE role::text
                WHEN 'admin'        THEN ?::uuid
                WHEN 'super_admin'  THEN ?::uuid
                WHEN 'tenant_admin' THEN ?::uuid
                WHEN 'group_admin'  THEN ?::uuid
                WHEN 'owner'        THEN ?::uuid
                WHEN 'restaurateur' THEN ?::uuid
                WHEN 'staff'        THEN ?::uuid
                WHEN 'manager'      THEN ?::uuid
                WHEN 'waiter'       THEN ?::uuid
                WHEN 'server'       THEN ?::uuid
                ELSE ?::uuid  -- défaut = CLIENT
              END AS role_id,
              COALESCE(auth_email, profile_email, id::text || '@nomail.local') AS email,
              -- Conserve le phone seulement pour la 1ère occurrence (phone_rank=1)
              CASE WHEN phone_rank = 1 THEN phone ELSE NULL END AS phone,
              COALESCE(NULLIF(encrypted_password, ''), ?) AS password_hash,
              COALESCE(NULLIF(first_name, ''), 'Prénom'),
              COALESCE(NULLIF(last_name, ''), 'Nom'),
              avatar_url,
              COALESCE(language, 'fr'),
              'active',
              true, true, true, true,
              COALESCE(created_at, now()),
              COALESCE(updated_at, now())
            FROM ranked
            """,
            RolesStep.ROLE_SUPERADMIN,   // admin
            RolesStep.ROLE_SUPERADMIN,   // super_admin
            RolesStep.ROLE_GROUP_ADMIN,  // tenant_admin
            RolesStep.ROLE_GROUP_ADMIN,  // group_admin
            RolesStep.ROLE_RESTAURATEUR, // owner
            RolesStep.ROLE_RESTAURATEUR, // restaurateur
            RolesStep.ROLE_STAFF,        // staff
            RolesStep.ROLE_STAFF,        // manager
            RolesStep.ROLE_STAFF,        // waiter
            RolesStep.ROLE_STAFF,        // server
            RolesStep.ROLE_CLIENT,       // ELSE (client + autres)
            fallbackHash                 // pour users sans encrypted_password
        );

        log.info("  users : {} rows insérées (fallback hash pour les sans-auth.users.encrypted_password)", inserted);

        // ─── Stats détaillées ──────────────────────────────────────────────────
        Long withFallback = jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE password_hash = ?", Long.class, fallbackHash);
        log.debug("  users avec mot de passe fallback (TestLocal2026!) : {}", withFallback);

        Long byRole = jdbc.queryForObject("SELECT count(*) FROM users WHERE role_id = ?::uuid",
            Long.class, RolesStep.ROLE_RESTAURATEUR);
        log.debug("  users role=RESTAURATEUR : {}", byRole);

        return inserted;
    }

    @Override
    public void validate() {
        long users = countTarget("users");
        long profiles = countLegacy("profiles");
        if (users != profiles) {
            throw new IllegalStateException("users : enterprise=" + users + " != legacy.profiles=" + profiles);
        }
        // Aucun email NULL (NOT NULL constraint enforced)
        Long nullEmails = jdbc.queryForObject("SELECT count(*) FROM users WHERE email IS NULL OR email=''", Long.class);
        if (nullEmails != null && nullEmails > 0) {
            throw new IllegalStateException("users : " + nullEmails + " rows avec email null/vide");
        }
        // Sanity check : pas de role_id orphelin
        Long orphans = jdbc.queryForObject(
            "SELECT count(*) FROM users u WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.id = u.role_id)",
            Long.class);
        if (orphans != null && orphans > 0) {
            throw new IllegalStateException("users : " + orphans + " role_id orphelins");
        }
    }
}
