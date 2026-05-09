-- V8 — Migration TenantEvent.status text → status_id FK entity_statuses
-- Valeurs prod observées : actif (10) / annule (3) / published (5)
-- Mapping legacy → canonique : published → publie

ALTER TABLE public.tenant_events
    ADD COLUMN IF NOT EXISTS status_id UUID
        REFERENCES public.entity_statuses(id);

UPDATE public.tenant_events t
SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'tenant_event'
  AND es.code = CASE t.status
    WHEN 'brouillon' THEN 'brouillon'
    WHEN 'publie'    THEN 'publie'
    WHEN 'published' THEN 'publie'   -- legacy EN
    WHEN 'actif'     THEN 'actif'
    WHEN 'annule'    THEN 'annule'
    WHEN 'termine'   THEN 'termine'
    ELSE 'actif'                     -- fallback safe
  END
  AND t.status_id IS NULL;

DO $$
DECLARE orphan_count INTEGER;
BEGIN
    SELECT count(*) INTO orphan_count FROM public.tenant_events WHERE status_id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'V8 backfill incomplet : % tenant_events sans status_id', orphan_count;
    END IF;
END $$;

ALTER TABLE public.tenant_events ALTER COLUMN status_id SET NOT NULL;
ALTER TABLE public.tenant_events DROP COLUMN IF EXISTS status;
CREATE INDEX IF NOT EXISTS idx_tenant_events_status_id ON public.tenant_events(status_id);
