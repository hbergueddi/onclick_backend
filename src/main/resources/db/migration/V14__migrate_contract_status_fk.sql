-- V14 — Migration contract/ (OnboardingRequest + OneclickHiInvoice + PartnerContract).

-- ─── 1. onboarding_requests ───────────────────────────────────────────────
ALTER TABLE public.onboarding_requests ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.onboarding_requests DISABLE TRIGGER USER;
UPDATE public.onboarding_requests o SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'onboarding_request'
  AND es.code = CASE o.status
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'approuvee'  THEN 'approuvee'
    WHEN 'approuvée'  THEN 'approuvee'
    WHEN 'refusee'    THEN 'refusee'
    WHEN 'refusée'    THEN 'refusee'
    ELSE 'en_attente'
  END
  AND o.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.onboarding_requests WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V14 onboarding : % rows', c; END IF; END $$;
ALTER TABLE public.onboarding_requests ENABLE TRIGGER USER;
ALTER TABLE public.onboarding_requests ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_onboarding_requests_status_id ON public.onboarding_requests(status_id);
CREATE TRIGGER trg_sync_status_onboarding_requests
    BEFORE INSERT OR UPDATE OF status, status_id ON public.onboarding_requests
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('onboarding_request');

-- ─── 2. oneclick_hi_invoices ──────────────────────────────────────────────
ALTER TABLE public.oneclick_hi_invoices ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.oneclick_hi_invoices DISABLE TRIGGER USER;
UPDATE public.oneclick_hi_invoices i SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'oneclick_hi_invoice'
  AND es.code = CASE i.status
    WHEN 'brouillon'     THEN 'brouillon'
    WHEN 'en_attente'    THEN 'en_attente'
    WHEN 'validee'       THEN 'validee'
    WHEN 'validée'       THEN 'validee'
    WHEN 'envoyee'       THEN 'envoyee'
    WHEN 'envoyée'       THEN 'envoyee'
    WHEN 'en_retard'     THEN 'en_retard'
    WHEN 'rappel_envoye' THEN 'rappel_envoye'
    WHEN 'rappel_envoyé' THEN 'rappel_envoye'
    WHEN 'payee'         THEN 'payee'
    WHEN 'payée'         THEN 'payee'
    WHEN 'payé'          THEN 'payee'
    WHEN 'annulee'       THEN 'annulee'
    WHEN 'annulée'       THEN 'annulee'
    ELSE 'brouillon'
  END
  AND i.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.oneclick_hi_invoices WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V14 invoices : % rows', c; END IF; END $$;
ALTER TABLE public.oneclick_hi_invoices ENABLE TRIGGER USER;
ALTER TABLE public.oneclick_hi_invoices ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_oneclick_hi_invoices_status_id ON public.oneclick_hi_invoices(status_id);
CREATE TRIGGER trg_sync_status_oneclick_hi_invoices
    BEFORE INSERT OR UPDATE OF status, status_id ON public.oneclick_hi_invoices
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('oneclick_hi_invoice');

-- ─── 3. partner_contracts ─────────────────────────────────────────────────
ALTER TABLE public.partner_contracts ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.partner_contracts DISABLE TRIGGER USER;
UPDATE public.partner_contracts c SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'partner_contract'
  AND es.code = CASE c.status
    WHEN 'prospect'        THEN 'prospect'
    WHEN 'en_negociation'  THEN 'en_negociation'
    WHEN 'en_négociation'  THEN 'en_negociation'
    WHEN 'envoye'          THEN 'envoye'
    WHEN 'envoyé'          THEN 'envoye'
    WHEN 'actif'           THEN 'actif'
    WHEN 'renouvellement'  THEN 'renouvellement'
    WHEN 'expire'          THEN 'expire'
    WHEN 'expiré'          THEN 'expire'
    WHEN 'resilie'         THEN 'resilie'
    WHEN 'résilié'         THEN 'resilie'
    ELSE 'prospect'
  END
  AND c.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.partner_contracts WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V14 contracts : % rows', c; END IF; END $$;
ALTER TABLE public.partner_contracts ENABLE TRIGGER USER;
ALTER TABLE public.partner_contracts ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_partner_contracts_status_id ON public.partner_contracts(status_id);
CREATE TRIGGER trg_sync_status_partner_contracts
    BEFORE INSERT OR UPDATE OF status, status_id ON public.partner_contracts
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('partner_contract');
