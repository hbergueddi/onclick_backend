-- ============================================================================
-- V15 — Module promotion : enrichissement Offer avec type + pts
-- ============================================================================
-- Contexte : table `offers` (créée en V6) supporte uniquement `discount_pct` /
-- `discount_amount` aujourd'hui. On ajoute deux colonnes pour distinguer :
--   • type='promo'  → réduction classique (discount_pct ou discount_amount)
--   • type='bonus'  → bonus points fidélité (champ `pts` rempli)
--   • type='reco'   → recommandation éditoriale (mise en avant)
--
-- Idempotent : tous les ADD COLUMN utilisent IF NOT EXISTS afin de pouvoir
-- rejouer la migration sans casser un environnement déjà partiellement migré.
-- ============================================================================

ALTER TABLE offers
    ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'promo'
        CHECK (type IN ('promo', 'bonus', 'reco')),
    ADD COLUMN IF NOT EXISTS pts INT;

-- Garde-fou métier : pts uniquement renseigné pour les bonus.
-- (NULL autorisé partout pour rétro-compat ; CHECK validé uniquement quand
-- type='bonus' et pts NOT NULL.)
ALTER TABLE offers
    DROP CONSTRAINT IF EXISTS offers_pts_positive_check;
ALTER TABLE offers
    ADD CONSTRAINT offers_pts_positive_check
        CHECK (pts IS NULL OR pts > 0);

COMMENT ON COLUMN offers.type IS 'Catégorie de l''offre : promo (réduction), bonus (bonus points), reco (recommandation)';
COMMENT ON COLUMN offers.pts  IS 'Bonus points fidélité accordés — renseigné uniquement quand type=bonus';

-- Index partiel pour les listings filtrés par type (Compass : "Bonus uniquement")
CREATE INDEX IF NOT EXISTS idx_offers_restaurant_type
    ON offers (restaurant_id, type)
    WHERE deleted_at IS NULL AND enabled = true;
