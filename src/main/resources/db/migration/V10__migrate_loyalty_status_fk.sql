-- V10 — Migration loyalty/ status text → status_id FK + trigger sync
-- 6 entités : elite_applications, elite_rsvps, gain_rule_requests,
-- point_distributions, redemption_otp_requests, scanned_tickets
-- Pattern : voir V9 (DISABLE TRIGGER USER pendant backfill, puis sync trigger).

-- ─── 1. elite_applications ──────────────────────────────────────────────────
ALTER TABLE public.elite_applications ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.elite_applications DISABLE TRIGGER USER;

UPDATE public.elite_applications a SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'elite_application'
  AND es.code = CASE a.status
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'approuvee'  THEN 'approuvee'
    WHEN 'approuvée'  THEN 'approuvee'
    WHEN 'refusee'    THEN 'refusee'
    WHEN 'refusée'    THEN 'refusee'
    ELSE 'en_attente'
  END
  AND a.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.elite_applications WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V10 elite_applications : % rows', c; END IF;
END $$;

ALTER TABLE public.elite_applications ENABLE TRIGGER USER;
ALTER TABLE public.elite_applications ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_elite_applications_status_id ON public.elite_applications(status_id);
CREATE TRIGGER trg_sync_status_elite_applications
    BEFORE INSERT OR UPDATE OF status, status_id ON public.elite_applications
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('elite_application');

-- ─── 2. elite_rsvps ─────────────────────────────────────────────────────────
ALTER TABLE public.elite_rsvps ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.elite_rsvps DISABLE TRIGGER USER;

UPDATE public.elite_rsvps r SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'elite_rsvp'
  AND es.code = CASE r.status
    WHEN 'confirmed'  THEN 'confirmee'
    WHEN 'confirmee'  THEN 'confirmee'
    WHEN 'confirmée'  THEN 'confirmee'
    WHEN 'cancelled'  THEN 'annulee'
    WHEN 'annulee'    THEN 'annulee'
    WHEN 'annulée'    THEN 'annulee'
    WHEN 'waitlist'   THEN 'liste_attente'
    WHEN 'liste_attente' THEN 'liste_attente'
    ELSE 'confirmee'
  END
  AND r.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.elite_rsvps WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V10 elite_rsvps : % rows', c; END IF;
END $$;

ALTER TABLE public.elite_rsvps ENABLE TRIGGER USER;
ALTER TABLE public.elite_rsvps ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_elite_rsvps_status_id ON public.elite_rsvps(status_id);
CREATE TRIGGER trg_sync_status_elite_rsvps
    BEFORE INSERT OR UPDATE OF status, status_id ON public.elite_rsvps
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('elite_rsvp');

-- ─── 3. gain_rule_requests ──────────────────────────────────────────────────
ALTER TABLE public.gain_rule_requests ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.gain_rule_requests DISABLE TRIGGER USER;

UPDATE public.gain_rule_requests r SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'gain_rule_request'
  AND es.code = CASE r.status
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'approuvee'  THEN 'approuvee'
    WHEN 'approuvée'  THEN 'approuvee'
    WHEN 'refusee'    THEN 'refusee'
    WHEN 'refusée'    THEN 'refusee'
    ELSE 'en_attente'
  END
  AND r.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.gain_rule_requests WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V10 gain_rule_requests : % rows', c; END IF;
END $$;

ALTER TABLE public.gain_rule_requests ENABLE TRIGGER USER;
ALTER TABLE public.gain_rule_requests ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_gain_rule_requests_status_id ON public.gain_rule_requests(status_id);
CREATE TRIGGER trg_sync_status_gain_rule_requests
    BEFORE INSERT OR UPDATE OF status, status_id ON public.gain_rule_requests
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('gain_rule_request');

-- ─── 4. point_distributions ─────────────────────────────────────────────────
ALTER TABLE public.point_distributions ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.point_distributions DISABLE TRIGGER USER;

UPDATE public.point_distributions p SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'point_distribution'
  AND es.code = CASE p.status
    WHEN 'planifie'   THEN 'planifie'
    WHEN 'planifiée'  THEN 'planifie'
    WHEN 'en_cours'   THEN 'en_cours'
    WHEN 'distribuee' THEN 'distribuee'
    WHEN 'distribuée' THEN 'distribuee'
    WHEN 'annulee'    THEN 'annulee'
    WHEN 'annulée'    THEN 'annulee'
    ELSE 'planifie'
  END
  AND p.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.point_distributions WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V10 point_distributions : % rows', c; END IF;
END $$;

ALTER TABLE public.point_distributions ENABLE TRIGGER USER;
ALTER TABLE public.point_distributions ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_point_distributions_status_id ON public.point_distributions(status_id);
CREATE TRIGGER trg_sync_status_point_distributions
    BEFORE INSERT OR UPDATE OF status, status_id ON public.point_distributions
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('point_distribution');

-- ─── 5. redemption_otp_requests ─────────────────────────────────────────────
ALTER TABLE public.redemption_otp_requests ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.redemption_otp_requests DISABLE TRIGGER USER;

UPDATE public.redemption_otp_requests r SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'redemption_otp_request'
  AND es.code = CASE r.status
    WHEN 'pending'    THEN 'en_attente'
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'consumed'   THEN 'consommee'
    WHEN 'consommee'  THEN 'consommee'
    WHEN 'consommée'  THEN 'consommee'
    WHEN 'expired'    THEN 'expiree'
    WHEN 'expiree'    THEN 'expiree'
    WHEN 'expirée'    THEN 'expiree'
    WHEN 'cancelled'  THEN 'annulee'
    WHEN 'annulee'    THEN 'annulee'
    WHEN 'annulée'    THEN 'annulee'
    ELSE 'en_attente'
  END
  AND r.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.redemption_otp_requests WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V10 redemption_otp_requests : % rows', c; END IF;
END $$;

ALTER TABLE public.redemption_otp_requests ENABLE TRIGGER USER;
ALTER TABLE public.redemption_otp_requests ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_redemption_otp_requests_status_id ON public.redemption_otp_requests(status_id);
CREATE TRIGGER trg_sync_status_redemption_otp_requests
    BEFORE INSERT OR UPDATE OF status, status_id ON public.redemption_otp_requests
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('redemption_otp_request');

-- ─── 6. scanned_tickets ─────────────────────────────────────────────────────
ALTER TABLE public.scanned_tickets ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.scanned_tickets DISABLE TRIGGER USER;

UPDATE public.scanned_tickets t SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'scanned_ticket'
  AND es.code = CASE t.status
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'valide'     THEN 'valide'
    WHEN 'validé'     THEN 'valide'
    WHEN 'rejete'     THEN 'rejete'
    WHEN 'rejeté'     THEN 'rejete'
    ELSE 'valide'
  END
  AND t.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.scanned_tickets WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V10 scanned_tickets : % rows', c; END IF;
END $$;

ALTER TABLE public.scanned_tickets ENABLE TRIGGER USER;
ALTER TABLE public.scanned_tickets ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_scanned_tickets_status_id ON public.scanned_tickets(status_id);
CREATE TRIGGER trg_sync_status_scanned_tickets
    BEFORE INSERT OR UPDATE OF status, status_id ON public.scanned_tickets
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('scanned_ticket');
