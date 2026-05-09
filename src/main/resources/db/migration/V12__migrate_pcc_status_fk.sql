-- V12 — Migration pcc/ (EventRsvp + SeminarRequest). Pattern V9.

-- ─── 1. event_rsvps ─────────────────────────────────────────────────────────
ALTER TABLE public.event_rsvps ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.event_rsvps DISABLE TRIGGER USER;
UPDATE public.event_rsvps r SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'event_rsvp'
  AND es.code = CASE r.status
    WHEN 'attending'      THEN 'confirmee'
    WHEN 'confirmee'      THEN 'confirmee'
    WHEN 'maybe'          THEN 'peut_etre'
    WHEN 'peut_etre'      THEN 'peut_etre'
    WHEN 'not_attending'  THEN 'pas_dispo'
    WHEN 'pas_dispo'      THEN 'pas_dispo'
    ELSE 'confirmee'
  END
  AND r.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.event_rsvps WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V12 event_rsvps : % rows', c; END IF; END $$;
ALTER TABLE public.event_rsvps ENABLE TRIGGER USER;
ALTER TABLE public.event_rsvps ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_event_rsvps_status_id ON public.event_rsvps(status_id);
CREATE TRIGGER trg_sync_status_event_rsvps
    BEFORE INSERT OR UPDATE OF status, status_id ON public.event_rsvps
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('event_rsvp');

-- ─── 2. seminar_requests ───────────────────────────────────────────────────
ALTER TABLE public.seminar_requests ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.seminar_requests DISABLE TRIGGER USER;
UPDATE public.seminar_requests s SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'seminar_request'
  AND es.code = CASE s.status
    WHEN 'demandee'     THEN 'demandee'
    WHEN 'demandée'     THEN 'demandee'
    WHEN 'en_traitement' THEN 'en_traitement'
    WHEN 'confirmee'    THEN 'confirmee'
    WHEN 'confirmée'    THEN 'confirmee'
    WHEN 'refusee'      THEN 'refusee'
    WHEN 'refusée'      THEN 'refusee'
    WHEN 'annulee'      THEN 'annulee'
    WHEN 'annulée'      THEN 'annulee'
    ELSE 'demandee'
  END
  AND s.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.seminar_requests WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V12 seminar_requests : % rows', c; END IF; END $$;
ALTER TABLE public.seminar_requests ENABLE TRIGGER USER;
ALTER TABLE public.seminar_requests ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_seminar_requests_status_id ON public.seminar_requests(status_id);
CREATE TRIGGER trg_sync_status_seminar_requests
    BEFORE INSERT OR UPDATE OF status, status_id ON public.seminar_requests
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('seminar_request');
