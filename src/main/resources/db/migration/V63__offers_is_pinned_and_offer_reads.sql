-- ════════════════════════════════════════════════════════════════════
-- V63 — Offres épinglées (is_pinned) + suivi lu/non-lu par client (offer_reads)
-- ════════════════════════════════════════════════════════════════════
-- Deux ajouts cohérents autour des offres (module modules/promotion) qui
-- alimentent ensemble la logique « épinglées non-lues » côté Pocket :
--
--   ITEM 2 — offers.is_pinned
--     Toggle booléen de mise en avant prioritaire d'une offre. Modifiable par
--     le resto via PATCH /api/offers/{id} (UPDATE:OFFERS), comme enabled.
--     Index PARTIEL sur les offres épinglées actives (le front ne liste qu'un
--     petit sous-ensemble épinglé → index ciblé, pas de coût sur le reste).
--
--   ITEM 3 — table offer_reads
--     1 ligne = (un user a lu une offre). UNIQUE(user_id, offer_id) → upsert
--     idempotent (re-marquer lu ne crée pas de doublon). Refs par UUID PLATS
--     (pas de @ManyToOne cross-module : le module promotion est CLOSED — même
--     convention que offer_impressions V21 / loyalty / reservation_guests).
--
-- Idempotent (IF NOT EXISTS) — re-jouable sans erreur cross-environnement.
-- ════════════════════════════════════════════════════════════════════

-- ─── ITEM 2 : colonne is_pinned sur offers ──────────────────────────────────
ALTER TABLE offers
    ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN offers.is_pinned IS
    'V63 — offre épinglée (mise en avant prioritaire Pocket). Combinée à offer_reads pour la logique « épinglées non-lues ». Défaut false.';

-- Index partiel : seules les offres épinglées ACTIVES sont listées en avant.
-- (deleted_at IS NULL + enabled = true : aligné sur idx_offers_restaurant_active.)
CREATE INDEX IF NOT EXISTS idx_offers_pinned
    ON offers(restaurant_id, expires_at)
    WHERE is_pinned = true AND deleted_at IS NULL AND enabled = true;

-- ─── ITEM 3 : table offer_reads ─────────────────────────────────────────────
-- read_at  : horodatage du dernier marquage lu (mis à jour à chaque re-lecture).
-- created_at : horodatage de création de la ligne (1er marquage). Stable.
CREATE TABLE IF NOT EXISTS offer_reads (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    offer_id    UUID         NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    read_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_offer_reads_user_offer UNIQUE (user_id, offer_id)
);

-- Lookup « toutes les offres lues par l'utilisateur courant » (GET /api/offers/reads).
CREATE INDEX IF NOT EXISTS idx_offer_reads_user
    ON offer_reads(user_id, read_at DESC);

-- Lookup inverse « qui a lu cette offre » (analytics / nettoyage cascade).
CREATE INDEX IF NOT EXISTS idx_offer_reads_offer
    ON offer_reads(offer_id);

COMMENT ON TABLE offer_reads IS
    'V63 — suivi lu/non-lu d''une offre par utilisateur. UNIQUE(user_id, offer_id) = upsert idempotent. Pilote la logique « épinglées non-lues » du front Pocket.';
