-- ════════════════════════════════════════════════════════════════════
-- V44 — Domaine « loyalty_plafonds » : plafonds / limites anti-abus Lounge
-- ════════════════════════════════════════════════════════════════════
-- La page admin FORGE PlafondsLimites lisait loyalty_plafonds via le shim
-- supabase.from("loyalty_plafonds") — table sans modèle Spring → lecture []
-- silencieuse. Décision produit : créer le vrai domaine (admin FORGE).
--
-- RBAC v2 senior strict : ressource dédiée LOYALTY_CAP accordée à SUPERADMIN
-- uniquement. @PreAuthorize = hasAuthority('VERB:LOYALTY_CAP'), jamais
-- isAuthenticated()/hasRole(). Pas de seed de valeurs métier (décision produit).
--
-- Idempotent. ⚠️ Après application : redis-cli FLUSHDB (nouvelle autorité).
-- ════════════════════════════════════════════════════════════════════

-- 1) Table loyalty_plafonds ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS loyalty_plafonds (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT          NOT NULL CHECK (length(trim(name)) > 0),
    scope       TEXT          NOT NULL DEFAULT 'global'
                CHECK (scope IN ('client', 'restaurant', 'global')),
    value       NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (value >= 0),
    unit        TEXT          NOT NULL DEFAULT 'points',
    description TEXT,
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    created_by  UUID          REFERENCES users(id) ON DELETE SET NULL,
    updated_by  UUID          REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_loyalty_plafonds_scope
    ON loyalty_plafonds(scope) WHERE deleted_at IS NULL;

COMMENT ON TABLE loyalty_plafonds IS
    'Plafonds / limites anti-abus du programme OneClick Lounge — gérés par l''admin (FORGE).';

-- 2) Trigger updated_at (idempotent) ──────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'update_updated_at_column')
       AND NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_loyalty_plafonds_updated_at') THEN
        CREATE TRIGGER trg_loyalty_plafonds_updated_at
            BEFORE UPDATE ON loyalty_plafonds
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- 3) Ressource RBAC LOYALTY_CAP (menu non-sidebar) ────────────────────
INSERT INTO menus (id, code, name, icon, sort_order)
SELECT gen_random_uuid(), 'LOYALTY_CAP', 'Plafonds & Limites', 'shield-alert', 903
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'LOYALTY_CAP');

-- 4) Grant VIEW/CREATE/UPDATE/DELETE:LOYALTY_CAP → SUPERADMIN uniquement ─
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE r.code = 'SUPERADMIN'
  AND m.code = 'LOYALTY_CAP'
  AND a.code IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE')
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
