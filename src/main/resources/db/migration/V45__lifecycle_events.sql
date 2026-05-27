-- ════════════════════════════════════════════════════════════════════
-- V45 — Domaine « lifecycle_events » : journal cycle de vie des restaurants
-- ════════════════════════════════════════════════════════════════════
-- La page admin GALAXY CycleDeVie lisait lifecycle_events via le shim
-- supabase.from("lifecycle_events") — table sans modèle Spring → lecture []
-- silencieuse. Journal append-only (inscription/validation/suspension/…).
--
-- RBAC v2 senior strict : ressource LIFECYCLE, VIEW + CREATE accordés à
-- SUPERADMIN uniquement (append-only → pas d'UPDATE/DELETE).
-- @PreAuthorize = hasAuthority('VERB:LIFECYCLE'), jamais isAuthenticated()/hasRole().
--
-- Immuable : étend CreatedAtEntity (created_at seul, pas updated_at/deleted_at).
-- Idempotent. ⚠️ Après application : redis-cli FLUSHDB (nouvelle autorité).
-- ════════════════════════════════════════════════════════════════════

-- 1) Table lifecycle_events ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS lifecycle_events (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID        REFERENCES restaurants(id) ON DELETE SET NULL,
    event_type    TEXT        NOT NULL CHECK (length(trim(event_type)) > 0),
    details       TEXT,
    actor         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_lifecycle_events_restaurant ON lifecycle_events(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_lifecycle_events_created    ON lifecycle_events(created_at DESC);

COMMENT ON TABLE lifecycle_events IS
    'Journal append-only du cycle de vie des restaurants (inscription, validation, suspension…) — vue admin GALAXY.';

-- 2) Ressource RBAC LIFECYCLE (menu non-sidebar) ──────────────────────
INSERT INTO menus (id, code, name, icon, sort_order)
SELECT gen_random_uuid(), 'LIFECYCLE', 'Cycle de vie', 'activity', 904
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'LIFECYCLE');

-- 3) Grant VIEW + CREATE:LIFECYCLE → SUPERADMIN (append-only) ──────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE r.code = 'SUPERADMIN'
  AND m.code = 'LIFECYCLE'
  AND a.code IN ('VIEW', 'CREATE')
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
