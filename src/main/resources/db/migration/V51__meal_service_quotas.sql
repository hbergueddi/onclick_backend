-- V51 — quotas Click&Go par créneau de service (/reservations · onglet Quotas).
--
-- L'entité MealService (table restaurant_services) ne modélisait que
-- {name, start_time, end_time}. L'écran admin "Quotas Click&Go" édite en plus
-- un quota de couverts par service (clickgo_quota), une capacité max, un type
-- (brunch/déjeuner/dîner) et un statut (actif/inactif). Sans ces colonnes, l'écran
-- restait sur le shim Supabase mort (lecture restaurant_services → []).
--
-- Idempotent (ADD COLUMN IF NOT EXISTS). Défauts non-destructifs :
--   clickgo_quota = 0  → l'UI affiche "∞" (pas de plafond) tant que non configuré
--   capacite_max  = 0
--   status        = 'actif'  → tous les services existants restent visibles
--   type          = NULL     → best-effort backfill depuis le nom (icône UI)
ALTER TABLE restaurant_services
    ADD COLUMN IF NOT EXISTS type          varchar(32),
    ADD COLUMN IF NOT EXISTS clickgo_quota integer      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS capacite_max  integer      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status        varchar(16)  NOT NULL DEFAULT 'actif';

-- Best-effort : aligne `type` sur le nom minuscule (brunch/déjeuner/dîner) pour
-- piloter l'icône + le matching réservation. Ne touche que les lignes non typées.
UPDATE restaurant_services SET type = lower(name) WHERE type IS NULL;

CREATE INDEX IF NOT EXISTS idx_restaurant_services_status
    ON restaurant_services(status);
