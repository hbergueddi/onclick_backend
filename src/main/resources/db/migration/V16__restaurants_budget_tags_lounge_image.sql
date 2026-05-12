-- ============================================================================
-- V16 — Restaurants enrichis : budget / tags / lounge_pts / image
-- ============================================================================
-- Contexte : la table `restaurants` (créée en V3) ne portait pas les attributs
-- éditoriaux exploités côté Pocket (Compass, Vault, MyReservations) :
--   • budget       — gamme prix ('€', '€€', '€€€') affichée sur la carte
--   • tags         — étiquettes thématiques ('marocain', 'rooftop', etc.)
--   • lounge_pts   — bonus points Lounge (programme fidélité premium)
--   • image        — URL de visuel hero (CDN/Storage), évite un GET /api/media/
--
-- Ces colonnes sont consommées par le frontend pour rendre les listes de
-- restaurants sans rappels supplémentaires. Toutes nullables / défaut, donc
-- rétro-compatibles avec les rows existantes.
--
-- Idempotent : `ADD COLUMN IF NOT EXISTS` permet de rejouer la migration sans
-- casser un environnement déjà partiellement migré.
-- ============================================================================

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS budget     TEXT,
    ADD COLUMN IF NOT EXISTS tags       TEXT[],
    ADD COLUMN IF NOT EXISTS lounge_pts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS image      TEXT;

-- Garde-fou métier : budget reste TEXT mais limité à 3 valeurs symboliques.
-- (NULL autorisé pour rétro-compat avec les rows existantes.)
ALTER TABLE restaurants
    DROP CONSTRAINT IF EXISTS restaurants_budget_check;
ALTER TABLE restaurants
    ADD CONSTRAINT restaurants_budget_check
        CHECK (budget IS NULL OR budget IN ('€', '€€', '€€€'));

-- Garde-fou métier : lounge_pts non négatif.
ALTER TABLE restaurants
    DROP CONSTRAINT IF EXISTS restaurants_lounge_pts_check;
ALTER TABLE restaurants
    ADD CONSTRAINT restaurants_lounge_pts_check
        CHECK (lounge_pts >= 0);

COMMENT ON COLUMN restaurants.budget     IS 'Gamme de prix symbolique (€, €€, €€€). NULL = non renseigné.';
COMMENT ON COLUMN restaurants.tags       IS 'Étiquettes thématiques libres (cuisine, ambiance, etc.) — array text[].';
COMMENT ON COLUMN restaurants.lounge_pts IS 'Bonus points Lounge accordés lors d''une visite — programme fidélité premium.';
COMMENT ON COLUMN restaurants.image      IS 'URL absolue du visuel hero (CDN/Storage). NULL = fallback front (Unsplash).';

-- Index GIN sur tags pour les recherches "où sont les restos avec ce tag ?"
-- consommées par Compass (filtre Catégorie / Ambiance).
CREATE INDEX IF NOT EXISTS idx_restaurants_tags
    ON restaurants USING GIN (tags)
    WHERE deleted_at IS NULL;
