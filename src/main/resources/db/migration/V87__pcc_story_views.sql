-- ════════════════════════════════════════════════════════════════════
-- V87 — story_views (tracking lectures stories, Gap #7) — port legacy 07/05
-- ════════════════════════════════════════════════════════════════════
-- Réintroduit le tracking de vues différé en V72 (« V1 = lecture simple ; découpe
-- ré-introductible plus tard sans casser ce module — table dédiée séparée »).
--
-- USE CASE (Instagram-style) :
--   - Home membre : cercles avatars ring gradient unread / grey read (1 fetch enrichi `viewed`)
--   - Viewer fullscreen : marque la story « vue » à l'ouverture (idempotent)
--   - Stats staff/admin : « vue par X membres » par story (view-counts)
--
-- ARCHITECTURE Spring (senior, comme V72) : 0 RPC / 0 trigger. La logique
-- (mark idempotent, enrichissement `viewed`, agrégation counts, ABAC) vit dans
-- PccStoryService. Ici juste la table + PK composite + index DDL.
--
-- 1 ligne par couple (story_id, user_id) — PK composite → upsert idempotent
-- (ON CONFLICT côté service via existsBy... avant save). FK CASCADE : supprimer
-- une story ou un user purge ses vues.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS pcc_story_views (
    story_id   uuid        NOT NULL REFERENCES pcc_stories(id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (story_id, user_id)
);

-- « stories vues par un user » (enrichissement `viewed` du feed membre).
CREATE INDEX IF NOT EXISTS idx_pcc_story_views_user
    ON pcc_story_views (user_id, viewed_at DESC);

-- « vues d'une story » (agrégation counts staff/admin). La PK couvre déjà
-- (story_id, user_id) en tête → un index dédié story_id est redondant pour le
-- COUNT GROUP BY ; on s'appuie sur la PK.

COMMENT ON TABLE pcc_story_views IS
    'Tracking lectures stories (Instagram-style). PK (story_id, user_id) → upsert idempotent. Gap #7 — port legacy story_views, logique dans PccStoryService (0 RPC/trigger).';
