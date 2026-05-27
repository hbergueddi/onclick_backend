-- ════════════════════════════════════════════════════════════════════
-- V43 — Domaine « loyalty_tier_rules » : paliers de fidélité PLATEFORME
-- ════════════════════════════════════════════════════════════════════
-- Contexte : les pages admin FORGE (RuleBuilderPanel, ReglesDeGain) et ProDesk
-- (ReglesCredit, bloc « Paliers globaux Lounge ») lisaient/écrivaient des paliers
-- de fidélité NOMMÉS et PLATEFORME (Ruby/Sapphire/Émeraude…) via l'ancien shim
-- supabase.from("gain_rules") — sans restaurant_id. Or le `gain_rules` Spring (V13)
-- est STRICTEMENT par-restaurant (restaurant_id NOT NULL, UNIQUE) et n'a pas les
-- colonnes name/type/min_ticket/max_points/enabled. Résultat : lectures = [] et
-- écritures = no-op silencieux. Décision produit : créer un vrai domaine dédié.
--
-- Distinction des 3 concepts loyalty (anti big-ball-of-mud) :
--   • gain_rules          — conversion PAR restaurant (1 règle/resto)
--   • tier_thresholds     — paliers client PAR points (seuil → nom de tier)
--   • loyalty_tier_rules  — paliers de conversion PLATEFORME nommés (CE FICHIER)
--
-- RBAC v2 senior strict : ressource dédiée LOYALTY_TIER, accordée à SUPERADMIN
-- uniquement (le front gate déjà `role === "admin"`). On NE réutilise PAS
-- CREATE:LOYALTY (déjà accordé au STAFF pour snap2earn → sur-autoriserait le staff
-- à éditer des paliers plateforme). @PreAuthorize = hasAuthority('VERB:LOYALTY_TIER'),
-- jamais isAuthenticated()/hasRole().
--
-- Portée tenant : paliers GLOBAUX (pas de tenant_id) — l'admin plateforme les gère
-- pour tous les tenants, conforme au comportement legacy (aucun filtre tenant).
-- Ajouter tenant_id plus tard si un besoin per-tenant émerge (pas d'over-engineering).
--
-- Idempotent (IF NOT EXISTS / NOT EXISTS). ⚠️ Après application : flusher le cache
-- userDetails (redis-cli FLUSHDB) sinon les sessions en cache n'ont pas
-- VIEW/CREATE/UPDATE/DELETE:LOYALTY_TIER → 403 jusqu'au TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- 1) Table loyalty_tier_rules ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS loyalty_tier_rules (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    TEXT          NOT NULL CHECK (length(trim(name)) > 0),
    description             TEXT,
    type                    TEXT          NOT NULL DEFAULT 'standard',
    conversion_rate         NUMERIC(6,4)  NOT NULL DEFAULT 0.1000
                            CHECK (conversion_rate >= 0 AND conversion_rate <= 1),
    min_ticket              NUMERIC(10,2) NOT NULL DEFAULT 0   CHECK (min_ticket >= 0),
    max_points_per_ticket   INTEGER       NOT NULL DEFAULT 500 CHECK (max_points_per_ticket >= 0),
    period_type             TEXT          NOT NULL DEFAULT 'month'
                            CHECK (period_type IN ('week', 'month')),
    period_value            INTEGER       NOT NULL DEFAULT 1   CHECK (period_value >= 1),
    benefit_duration_days   INTEGER       NOT NULL DEFAULT 90  CHECK (benefit_duration_days >= 0),
    min_spend_monthly       NUMERIC(10,2) NOT NULL DEFAULT 0   CHECK (min_spend_monthly >= 0),
    enabled                 BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    created_by              UUID          REFERENCES users(id) ON DELETE SET NULL,
    updated_by              UUID          REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_loyalty_tier_rules_enabled
    ON loyalty_tier_rules(enabled) WHERE deleted_at IS NULL;

COMMENT ON TABLE loyalty_tier_rules IS
    'Paliers de fidélité plateforme (OneClick Lounge) — règles de conversion nommées gérées par l''admin (FORGE). Distinct de gain_rules (par restaurant) et tier_thresholds (paliers par points).';

-- 2) Trigger updated_at (réutilise le helper générique s'il existe, idempotent) ─
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'update_updated_at_column')
       AND NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_loyalty_tier_rules_updated_at') THEN
        CREATE TRIGGER trg_loyalty_tier_rules_updated_at
            BEFORE UPDATE ON loyalty_tier_rules
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- 3) Ressource RBAC LOYALTY_TIER (menu non-sidebar : path/parent NULL) ─────────
INSERT INTO menus (id, code, name, icon, sort_order)
SELECT gen_random_uuid(), 'LOYALTY_TIER', 'Paliers de fidélité', 'crown', 902
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'LOYALTY_TIER');

-- 4) Grant VIEW/CREATE/UPDATE/DELETE:LOYALTY_TIER → SUPERADMIN uniquement ──────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE r.code = 'SUPERADMIN'
  AND m.code = 'LOYALTY_TIER'
  AND a.code IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE')
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
