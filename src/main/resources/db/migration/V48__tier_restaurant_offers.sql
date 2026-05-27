-- ════════════════════════════════════════════════════════════════════
-- V48 — Domaine « tier_restaurant_offers » : offres fidélité PAR niveau & resto
-- ════════════════════════════════════════════════════════════════════
-- La page admin FORGE TierOffersMatrix (CRUD matrice resto × tier) et la page
-- ProDesk OfferJet (lecture « Offres fidélité par niveau ») lisaient/écrivaient
-- tier_restaurant_offers via le shim supabase → lectures [] / écritures no-op.
-- Concept distinct des offres éditoriales (offers) : mappe un tier (Ruby/Sapphire/
-- Émeraude) à un avantage (remise/cadeau/priorité…) pour un restaurant donné.
--
-- RBAC v2 senior strict : ressource TIER_OFFER.
--   • VIEW   → SUPERADMIN + RESTAURATEUR + GROUP_ADMIN (le resto lit ses offres,
--              scopé par ABAC RestaurantAccessGuard sur l'endpoint by-restaurant).
--   • CREATE/UPDATE/DELETE → SUPERADMIN uniquement (gestion FORGE).
-- @PreAuthorize = hasAuthority('VERB:TIER_OFFER'), jamais isAuthenticated()/hasRole().
--
-- Idempotent. ⚠️ Après application : redis-cli FLUSHDB (nouvelle autorité).
-- ════════════════════════════════════════════════════════════════════

-- 1) Table tier_restaurant_offers ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS tier_restaurant_offers (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID        NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    tier_name     TEXT        NOT NULL,
    offer_label   TEXT        NOT NULL CHECK (length(trim(offer_label)) > 0),
    offer_type    TEXT        NOT NULL DEFAULT 'remise',
    offer_value   TEXT,
    description   TEXT,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,
    created_by    UUID        REFERENCES users(id) ON DELETE SET NULL,
    updated_by    UUID        REFERENCES users(id) ON DELETE SET NULL
);

-- 1 offre par (restaurant, tier) parmi les lignes vivantes (upsert TierOffersMatrix).
CREATE UNIQUE INDEX IF NOT EXISTS uniq_tier_offer_resto_tier
    ON tier_restaurant_offers(restaurant_id, tier_name) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_tier_offers_restaurant
    ON tier_restaurant_offers(restaurant_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE tier_restaurant_offers IS
    'Offres fidélité par niveau (Ruby/Sapphire/Émeraude) et par restaurant — gérées FORGE, lues ProDesk OfferJet.';

-- 2) Trigger updated_at (idempotent) ──────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'update_updated_at_column')
       AND NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_tier_restaurant_offers_updated_at') THEN
        CREATE TRIGGER trg_tier_restaurant_offers_updated_at
            BEFORE UPDATE ON tier_restaurant_offers
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- 3) Ressource RBAC TIER_OFFER (menu non-sidebar) ─────────────────────
INSERT INTO menus (id, code, name, icon, sort_order)
SELECT gen_random_uuid(), 'TIER_OFFER', 'Offres par niveau', 'gift', 906
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'TIER_OFFER');

-- 4a) VIEW:TIER_OFFER → SUPERADMIN + RESTAURATEUR + GROUP_ADMIN ────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE r.code IN ('SUPERADMIN', 'RESTAURATEUR', 'GROUP_ADMIN')
  AND m.code = 'TIER_OFFER'
  AND a.code = 'VIEW'
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );

-- 4b) CREATE/UPDATE/DELETE:TIER_OFFER → SUPERADMIN uniquement (FORGE) ──
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE r.code = 'SUPERADMIN'
  AND m.code = 'TIER_OFFER'
  AND a.code IN ('CREATE', 'UPDATE', 'DELETE')
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
