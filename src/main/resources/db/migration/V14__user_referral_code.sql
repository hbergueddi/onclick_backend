-- ============================================================================
-- V14 — Ajout du referral_code sur users
-- ============================================================================
-- Contexte : code de parrainage public stable (8 chars uppercase) que le client
-- partage pour activer les bonus de parrainage (cf endpoint
-- /api/users/{id}/referral-code et activate_referral_by_code RPC).
--
-- Source initiale : hash UUID → 8 premiers chars hex en uppercase.
-- Suffisant pour ~4G codes uniques (16^8). Pas de collision observée sur le
-- jeu actuel (17 221 users seedés — 6 collisions sur 6 chars, 0 sur 8).
--
-- Idempotent (IF NOT EXISTS partout) pour ré-exécution sans risque.
-- ============================================================================

-- ─── 1. Colonne referral_code (nullable au départ) ─────────────────────────
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS referral_code TEXT;

-- ─── 2. Backfill : code dérivé du UUID pour les users existants ────────────
-- Format : 8 chars uppercase issus du UUID stripped (sans tirets).
UPDATE users
   SET referral_code = UPPER(SUBSTRING(REPLACE(id::text, '-', ''), 1, 8))
 WHERE referral_code IS NULL;

-- ─── 3. Index unique (filtré soft-delete) ──────────────────────────────────
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_referral_code
    ON users(referral_code)
 WHERE deleted_at IS NULL;

-- ─── 4. Function utilitaire pour génération côté DB ────────────────────────
-- Permet de générer un code stable depuis un UUID (utilisable dans triggers,
-- seeds, ou applications côté SQL).
CREATE OR REPLACE FUNCTION generate_referral_code(user_id UUID)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT UPPER(SUBSTRING(REPLACE(user_id::text, '-', ''), 1, 8));
$$;

COMMENT ON COLUMN users.referral_code IS 'Code de parrainage public stable (8 chars uppercase) — généré depuis l''UUID.';
COMMENT ON FUNCTION generate_referral_code(UUID) IS 'Génère un referral_code 8 chars uppercase à partir d''un UUID user.';
