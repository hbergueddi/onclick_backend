-- ════════════════════════════════════════════════════════════════════
-- V20 — Sprint D : Enrich events pour features Elite club
-- ════════════════════════════════════════════════════════════════════
-- Le schéma V6 events était minimal (id, tenant_id, title, description,
-- event_type, event_at, capacity). Pour porter les features Elite legacy :
--   • min_tier        — tier minimum requis (Ruby/Sapphire/Émeraude/Black)
--   • places_taken    — compteur RSVPs denormalisé (fast read sans COUNT)
--   • image_url       — visuel event (S3 path)
--   • location_name   — lieu (pour events hors resto, ex "Hôtel La Mamounia")
--   • is_active       — flag visibilité (admin peut désactiver sans delete)
--   • event_end       — timestamp fin (vs event_at = début)
--
-- ALTER TABLE event_participations ADD plus_one_name (cas Elite +1 invité).
-- ════════════════════════════════════════════════════════════════════

-- ─── 1. Enrichir events ──────────────────────────────────────────────
ALTER TABLE events
    ADD COLUMN min_tier      TEXT
        CHECK (min_tier IS NULL OR min_tier IN ('Ruby', 'Sapphire', 'Émeraude', 'Black')),
    ADD COLUMN places_taken  INT NOT NULL DEFAULT 0
        CHECK (places_taken >= 0),
    ADD COLUMN image_url     TEXT,
    ADD COLUMN location_name TEXT,
    ADD COLUMN is_active     BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN event_end     TIMESTAMPTZ;

-- Index ciblé : events actifs futurs (cas usage Pocket / EliteClub)
CREATE INDEX idx_events_active_future
    ON events(event_at)
    WHERE deleted_at IS NULL AND is_active = true;

-- Index par min_tier (filtre admin Elite)
CREATE INDEX idx_events_min_tier
    ON events(min_tier)
    WHERE deleted_at IS NULL AND min_tier IS NOT NULL;

-- ─── 2. Enrichir event_participations ────────────────────────────────
ALTER TABLE event_participations
    ADD COLUMN plus_one_name TEXT;

-- ─── 3. Constraint cap places_taken (anti-overflow) ──────────────────
-- Le service backend doit vérifier avant INSERT participation :
--   capacity != NULL AND places_taken < capacity
-- Pas de constraint DB hard (capacity peut être NULL = illimité).

COMMENT ON COLUMN events.min_tier IS
    'Tier minimum requis pour RSVP (null = ouvert à tous). Cf Elite club.';
COMMENT ON COLUMN events.places_taken IS
    'Compteur denormalisé des RSVPs going+maybe. Maj atomique via @Transactional.';
COMMENT ON COLUMN events.is_active IS
    'Flag visibilité — admin peut masquer sans soft-delete (réversible).';
COMMENT ON COLUMN event_participations.plus_one_name IS
    'Nom de l''accompagnant (cas Elite +1 invité). Null = solo.';
