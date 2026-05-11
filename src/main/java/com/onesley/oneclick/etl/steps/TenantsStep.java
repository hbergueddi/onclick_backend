package com.onesley.oneclick.etl.steps;

import com.onesley.oneclick.etl.EtlStep;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 1 — Tenants + TenantBranding + TenantFeature + CompanySettings.
 *
 * <p>Mapping :
 * <ul>
 *   <li>{@code legacy.tenants} → {@code tenants} (preserve UUIDs, drop features jsonb)</li>
 *   <li>{@code legacy.tenant_branding} (singular) → {@code tenant_brandings} (plural, PK=tenant_id)</li>
 *   <li>{@code legacy.tenant_features} (PK = tenant_id + feature_key) → {@code tenant_features} (id+tenant_id+feature_code)</li>
 *   <li>{@code legacy.company_settings} → {@code company_settings} (1 row legacy → 1 row par tenant enterprise,
 *       car enterprise impose UNIQUE(tenant_id))</li>
 * </ul>
 */
@Component
@Profile("etl")
public class TenantsStep extends EtlStep.AbstractEtlStep {

    public TenantsStep(JdbcTemplate jdbc, TransactionTemplate tx) {
        super(jdbc, tx);
    }

    @Override public String getName() { return "tenants"; }

    @Override
    public String[] getTargetTables() {
        // FK reverse order pour TRUNCATE
        return new String[] { "company_settings", "tenant_features", "tenant_brandings", "tenants" };
    }

    @Override
    public long migrate() {
        // ─── tenants (FK : aucune) ───────────────────────────────────────────
        // legacy.status = 'actif' (FR) → enterprise.status = 'active' (EN, CHECK constraint)
        long tn = jdbc.update("""
            INSERT INTO tenants (id, slug, name, status, created_at, updated_at)
            SELECT
              t.id,
              t.slug,
              t.name,
              CASE
                WHEN t.status IN ('actif', 'active') THEN 'active'
                WHEN t.status IN ('paused', 'pause') THEN 'paused'
                WHEN t.status IN ('archived', 'archive', 'inactif') THEN 'archived'
                ELSE 'active'
              END,
              COALESCE(t.created_at, now()),
              COALESCE(t.updated_at, now())
            FROM legacy.tenants t
            """);
        log.debug("  tenants : {} rows", tn);

        // ─── company_settings (FK : tenants, UNIQUE tenant_id) ───────────────
        // Legacy : 1 row partagée. Enterprise : 1 row par tenant.
        // → On duplique le template legacy pour chaque tenant.
        long cs = jdbc.update("""
            INSERT INTO company_settings (id, tenant_id, raison_sociale, ice, rib, tva_rate, created_at, updated_at)
            SELECT
              gen_random_uuid(),
              t.id,
              COALESCE(cs.raison_sociale, 'OneClick Maroc'),
              COALESCE(cs.numero_ice, ''),
              COALESCE(cs.rib, ''),
              COALESCE(cs.tva_rate, 20.00),
              COALESCE(cs.created_at, now()),
              COALESCE(cs.updated_at, now())
            FROM legacy.tenants t
            LEFT JOIN legacy.company_settings cs ON true
            """);
        log.debug("  company_settings : {} rows (1 row legacy × {} tenants)", cs, tn);

        // ─── tenant_brandings (FK : tenants, PK=tenant_id) ───────────────────
        // Pas de FK violations possibles si on a inséré tous les tenants au-dessus.
        long tb = jdbc.update("""
            INSERT INTO tenant_brandings (tenant_id, logo_url, primary_color, accent_color, custom_domain, created_at, updated_at)
            SELECT
              tb.tenant_id,
              tb.logo_url,
              tb.primary_color,
              tb.accent_color,
              tb.custom_domain,
              COALESCE(tb.updated_at, now()),
              COALESCE(tb.updated_at, now())
            FROM legacy.tenant_branding tb
            WHERE EXISTS (SELECT 1 FROM tenants t WHERE t.id = tb.tenant_id)
            """);
        log.debug("  tenant_brandings : {} rows", tb);

        // ─── tenant_features (rename feature_key → feature_code, generate id) ─
        long tf = jdbc.update("""
            INSERT INTO tenant_features (id, tenant_id, feature_code, enabled, created_at, updated_at)
            SELECT
              gen_random_uuid(),
              tf.tenant_id,
              tf.feature_key,
              COALESCE(tf.enabled, true),
              COALESCE(tf.updated_at, now()),
              COALESCE(tf.updated_at, now())
            FROM legacy.tenant_features tf
            WHERE EXISTS (SELECT 1 FROM tenants t WHERE t.id = tf.tenant_id)
            """);
        log.debug("  tenant_features : {} rows", tf);

        return tn + cs + tb + tf;
    }

    @Override
    public void validate() {
        long tenants = countTarget("tenants");
        long legacy = countLegacy("tenants");
        if (tenants != legacy) {
            throw new IllegalStateException("tenants : enterprise=" + tenants + " != legacy=" + legacy);
        }
        long companySettings = countTarget("company_settings");
        if (companySettings != tenants) {
            throw new IllegalStateException("company_settings should == tenants (one per tenant)");
        }
    }
}
