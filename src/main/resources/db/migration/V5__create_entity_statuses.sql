-- ============================================================================
-- V5 — Table de référence centralisée des statuts métier
-- ============================================================================
--
-- Contexte : retour senior dev (passe N+1, mai 2026). Les ~32 entités métier
-- ayant un champ "status" String hardcodé sont migrées vers une table de
-- référence unique avec un discriminator entity_type. Bénéfices :
--
--   * Typage strict : impossible d'assigner un statut "reservation" à une
--     "support_ticket" — la jointure est sur (entity_type, code).
--   * Normalisation : les inconsistances orthographiques relevées en prod
--     (approuvée vs approuvee, refusee vs refusé vs refused, …) sont
--     résolues via le seed canonique (V6).
--   * i18n natif : label_fr + label_en sans toucher aux entités métier.
--   * Workflow : flag is_terminal pour identifier les statuts finaux côté UI.
--   * Modifiable runtime : on peut désactiver / créer un statut sans DDL.
--
-- Pattern senior :
--   * UNIQUE (entity_type, code) — empêche les doublons sémantiques.
--   * Index dédié sur (entity_type, code) pour les lookups fréquents.
--   * Soft-delete via is_active (jamais de DELETE en prod : on désactive).
--   * Audit timestamps standards (created_at + updated_at).
--
-- Migration phasée :
--   * V5 (ici)       : création table + index — vide.
--   * V6 (à venir)   : seed des codes canoniques (~120 INSERTS).
--   * V7..N (à venir): par entité métier, ajout colonne status_id + backfill
--                      + DROP de l'ancien status text.
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.entity_statuses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type  TEXT NOT NULL,
    code         TEXT NOT NULL,
    label_fr     TEXT NOT NULL,
    label_en     TEXT,
    description  TEXT,
    sort_order   INTEGER NOT NULL DEFAULT 0,
    is_terminal  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT entity_statuses_entity_code_uq UNIQUE (entity_type, code)
);

-- Lookup principal : findByEntityTypeAndCode
CREATE INDEX IF NOT EXISTS idx_entity_statuses_lookup
    ON public.entity_statuses (entity_type, code);

-- Listing par entity_type filtré actif (UI dropdowns)
CREATE INDEX IF NOT EXISTS idx_entity_statuses_active_listing
    ON public.entity_statuses (entity_type, sort_order)
    WHERE is_active = TRUE;

COMMENT ON TABLE public.entity_statuses IS
    'Table de référence des statuts métier de toutes les entités. Discriminator entity_type.';
COMMENT ON COLUMN public.entity_statuses.entity_type IS
    'Discriminator : nom logique de l''entité métier (reservation, partner_contract, etc.).';
COMMENT ON COLUMN public.entity_statuses.code IS
    'Code canonique du statut (français snake_case sans accent : actif, en_attente, …).';
COMMENT ON COLUMN public.entity_statuses.is_terminal IS
    'Statut final du workflow ? Utilisé en UI pour distinguer les transitions possibles.';
COMMENT ON COLUMN public.entity_statuses.is_active IS
    'Soft-delete : un statut désactivé reste référencé par les rows existantes mais n''apparaît plus dans les dropdowns.';
