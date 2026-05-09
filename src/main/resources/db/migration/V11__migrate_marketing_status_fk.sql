-- V11 — Migration marketing/ (PromoNotificationRequest + Referral). Pattern V9.

-- ─── 1. promo_notification_requests ────────────────────────────────────────
ALTER TABLE public.promo_notification_requests ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.promo_notification_requests DISABLE TRIGGER USER;
UPDATE public.promo_notification_requests p SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'promo_notification_request'
  AND es.code = CASE p.status
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'approuvee'  THEN 'approuvee'
    WHEN 'approuvée'  THEN 'approuvee'
    WHEN 'envoyee'    THEN 'envoyee'
    WHEN 'envoyée'    THEN 'envoyee'
    WHEN 'rejetee'    THEN 'rejetee'
    WHEN 'rejetée'    THEN 'rejetee'
    ELSE 'en_attente'
  END
  AND p.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.promo_notification_requests WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V11 promo : % rows', c; END IF; END $$;
ALTER TABLE public.promo_notification_requests ENABLE TRIGGER USER;
ALTER TABLE public.promo_notification_requests ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_promo_notif_status_id ON public.promo_notification_requests(status_id);
CREATE TRIGGER trg_sync_status_promo_notification_requests
    BEFORE INSERT OR UPDATE OF status, status_id ON public.promo_notification_requests
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('promo_notification_request');

-- ─── 2. referrals ──────────────────────────────────────────────────────────
ALTER TABLE public.referrals ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.referrals DISABLE TRIGGER USER;
UPDATE public.referrals r SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'referral'
  AND es.code = CASE r.status
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'actif'      THEN 'actif'
    WHEN 'expire'     THEN 'expire'
    WHEN 'expiré'     THEN 'expire'
    ELSE 'en_attente'
  END
  AND r.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.referrals WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V11 referrals : % rows', c; END IF; END $$;
ALTER TABLE public.referrals ENABLE TRIGGER USER;
ALTER TABLE public.referrals ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_referrals_status_id ON public.referrals(status_id);
CREATE TRIGGER trg_sync_status_referrals
    BEFORE INSERT OR UPDATE OF status, status_id ON public.referrals
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('referral');
