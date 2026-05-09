-- ============================================================================
-- V7 — Migration Tenant.status text → status_id FK entity_statuses
-- ============================================================================
--
-- Pilote de la Phase 3 : remplacer le champ text status par une FK vers
-- entity_statuses(id) avec discriminator entity_type='tenant'.
--
-- Étapes :
--   1. Ajouter colonne status_id UUID nullable (transition)
--   2. Backfill via lookup canonique avec normalisation orthographique
--   3. Vérifier 0 ligne sans status_id (sécurité avant NOT NULL)
--   4. NOT NULL + FK enforcer
--   5. DROP CHECK constraint legacy + DROP COLUMN status text
--
-- Idempotent : ALTER TABLE IF NOT EXISTS / IF EXISTS partout.
-- ============================================================================

-- 1. Ajout colonne nullable
ALTER TABLE public.tenants
    ADD COLUMN IF NOT EXISTS status_id UUID
        REFERENCES public.entity_statuses(id);

-- 2. Backfill (mapping legacy → canonique)
--    Valeurs prod observées : actif / suspendu / archivé
--    Code canonique : actif / suspendu / archive (sans accent)
UPDATE public.tenants t
SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'tenant'
  AND es.code = CASE t.status
    WHEN 'actif'    THEN 'actif'
    WHEN 'suspendu' THEN 'suspendu'
    WHEN 'archivé'  THEN 'archive'   -- normalisation accent
    WHEN 'archive'  THEN 'archive'   -- déjà sans accent (defensive)
    ELSE 'actif'                     -- fallback safe (devrait être impossible)
  END
  AND t.status_id IS NULL;

-- 3. Sanity check : aucune ligne sans status_id
DO $$
DECLARE
    orphan_count INTEGER;
BEGIN
    SELECT count(*) INTO orphan_count FROM public.tenants WHERE status_id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'V7 backfill incomplet : % tenants sans status_id (status text inattendu ?)', orphan_count;
    END IF;
END $$;

-- 4. Enforcer NOT NULL
ALTER TABLE public.tenants ALTER COLUMN status_id SET NOT NULL;

-- 5. DROP CHECK legacy + ancienne colonne text
ALTER TABLE public.tenants DROP CONSTRAINT IF EXISTS tenants_status_check;
ALTER TABLE public.tenants DROP COLUMN IF EXISTS status;

-- Index pour les requêtes WHERE status_id (filtres dashboard admin)
CREATE INDEX IF NOT EXISTS idx_tenants_status_id ON public.tenants(status_id);
