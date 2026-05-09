-- V15 — Migration support/ (NoShowDispute + SupportTicket).

-- ─── 1. no_show_disputes ──────────────────────────────────────────────────
ALTER TABLE public.no_show_disputes ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.no_show_disputes DISABLE TRIGGER USER;
UPDATE public.no_show_disputes d SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'no_show_dispute'
  AND es.code = CASE d.status
    WHEN 'pending'    THEN 'en_attente'
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'accepted'   THEN 'acceptee'
    WHEN 'acceptee'   THEN 'acceptee'
    WHEN 'acceptée'   THEN 'acceptee'
    WHEN 'refused'    THEN 'refusee'
    WHEN 'refusee'    THEN 'refusee'
    WHEN 'refusée'    THEN 'refusee'
    ELSE 'en_attente'
  END
  AND d.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.no_show_disputes WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V15 disputes : % rows', c; END IF; END $$;
ALTER TABLE public.no_show_disputes ENABLE TRIGGER USER;
ALTER TABLE public.no_show_disputes ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_no_show_disputes_status_id ON public.no_show_disputes(status_id);
CREATE TRIGGER trg_sync_status_no_show_disputes
    BEFORE INSERT OR UPDATE OF status, status_id ON public.no_show_disputes
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('no_show_dispute');

-- ─── 2. support_tickets ───────────────────────────────────────────────────
ALTER TABLE public.support_tickets ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.support_tickets DISABLE TRIGGER USER;
UPDATE public.support_tickets t SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'support_ticket'
  AND es.code = CASE t.status
    WHEN 'ouvert'     THEN 'ouvert'
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'en_cours'   THEN 'en_cours'
    WHEN 'resolu'     THEN 'resolu'
    WHEN 'résolu'     THEN 'resolu'
    WHEN 'ferme'      THEN 'ferme'
    WHEN 'fermé'      THEN 'ferme'
    ELSE 'ouvert'
  END
  AND t.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.support_tickets WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V15 tickets : % rows', c; END IF; END $$;
ALTER TABLE public.support_tickets ENABLE TRIGGER USER;
ALTER TABLE public.support_tickets ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_support_tickets_status_id ON public.support_tickets(status_id);
CREATE TRIGGER trg_sync_status_support_tickets
    BEFORE INSERT OR UPDATE OF status, status_id ON public.support_tickets
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('support_ticket');
