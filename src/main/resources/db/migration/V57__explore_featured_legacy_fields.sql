-- ════════════════════════════════════════════════════════════════════
-- V57 — Explore Featured : champs éditoriaux legacy (label, notes, created_by)
-- ════════════════════════════════════════════════════════════════════
-- La page admin GALAXY « Pilotage Explore » (PilotageExplore.tsx) collecte un
-- multi-label affiché côté client + des notes internes admin + l'auteur de la
-- curation. Or explore_featured (V21) ne portait que rank/enabled/starts_at/
-- ends_at → ces champs étaient silencieusement perdus à l'upsert (le DTO ne les
-- transportait pas) et toujours rendus vides (« — ») dans la table.
--
-- Cette migration aligne le schéma sur l'UI :
--   • label      — étiquettes affichées (CSV : « Nouveau, Coup de cœur »)
--   • notes      — notes internes admin (non exposées au client)
--   • created_by — auteur de la mise en avant (audit léger, pas de FK)
--
-- updated_at existe déjà (V21) et est porté par TimestampedEntity → non touché.
-- Idempotent (ADD COLUMN IF NOT EXISTS). ddl-auto=validate : l'entity
-- ExploreFeatured mappe désormais ces 3 colonnes.
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE explore_featured ADD COLUMN IF NOT EXISTS label      TEXT;
ALTER TABLE explore_featured ADD COLUMN IF NOT EXISTS notes      TEXT;
ALTER TABLE explore_featured ADD COLUMN IF NOT EXISTS created_by UUID;

COMMENT ON COLUMN explore_featured.label      IS 'Étiquettes éditoriales affichées côté client (CSV).';
COMMENT ON COLUMN explore_featured.notes      IS 'Notes internes admin (non exposées au client).';
COMMENT ON COLUMN explore_featured.created_by IS 'Auteur de la mise en avant (audit léger, pas de FK).';
