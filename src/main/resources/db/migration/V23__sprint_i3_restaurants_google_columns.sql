-- ════════════════════════════════════════════════════════════════════
-- V23 — Sprint I.3 : colonnes Google Places sur restaurants
-- ════════════════════════════════════════════════════════════════════
--
-- Le service GooglePlacesEnrichmentService a besoin de colonnes Google
-- sur `public.restaurants`. Elles existent dans `legacy.restaurants` mais
-- pas dans le schéma Spring monolith.
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS google_place_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS google_rating NUMERIC(2,1),
    ADD COLUMN IF NOT EXISTS google_reviews_count INTEGER,
    ADD COLUMN IF NOT EXISTS google_updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS website_url TEXT,
    ADD COLUMN IF NOT EXISTS opening_hours JSONB,
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10,7);

CREATE INDEX IF NOT EXISTS idx_restaurants_google_place_id ON restaurants(google_place_id) WHERE google_place_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_restaurants_geo ON restaurants(latitude, longitude) WHERE latitude IS NOT NULL;
