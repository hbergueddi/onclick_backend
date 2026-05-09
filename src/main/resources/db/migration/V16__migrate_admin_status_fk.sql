-- V16 — Migration admin/ (FraudAlert + MonitorLog + SystemHealthCheck + TeamInvitation).

-- ─── 1. fraud_alerts ──────────────────────────────────────────────────────
ALTER TABLE public.fraud_alerts ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.fraud_alerts DISABLE TRIGGER USER;
UPDATE public.fraud_alerts a SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'fraud_alert'
  AND es.code = CASE a.status
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'en_cours'   THEN 'en_cours'
    WHEN 'resolue'    THEN 'resolue'
    WHEN 'résolue'    THEN 'resolue'
    WHEN 'rejetee'    THEN 'rejetee'
    WHEN 'rejetée'    THEN 'rejetee'
    ELSE 'en_attente'
  END
  AND a.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.fraud_alerts WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V16 fraud_alerts : % rows', c; END IF; END $$;
ALTER TABLE public.fraud_alerts ENABLE TRIGGER USER;
ALTER TABLE public.fraud_alerts ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_fraud_alerts_status_id ON public.fraud_alerts(status_id);
CREATE TRIGGER trg_sync_status_fraud_alerts
    BEFORE INSERT OR UPDATE OF status, status_id ON public.fraud_alerts
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('fraud_alert');

-- ─── 2. monitor_logs (status nullable côté DB) ─────────────────────────────
-- NB : monitor_logs.status est nullable d'après le DDL Java. On garde nullable.
ALTER TABLE public.monitor_logs ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.monitor_logs DISABLE TRIGGER USER;
UPDATE public.monitor_logs m SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'monitor_log'
  AND es.code = CASE m.status
    WHEN 'ok'      THEN 'ok'
    WHEN 'OK'      THEN 'ok'
    WHEN 'pending' THEN 'pending'
    WHEN 'warning' THEN 'warning'
    WHEN 'error'   THEN 'error'
    WHEN 'erreur'  THEN 'error'
    ELSE 'ok'
  END
  AND m.status IS NOT NULL  -- nullable
  AND m.status_id IS NULL;
ALTER TABLE public.monitor_logs ENABLE TRIGGER USER;
-- monitor_logs.status_id reste nullable (cohérent avec status nullable)
CREATE INDEX IF NOT EXISTS idx_monitor_logs_status_id ON public.monitor_logs(status_id) WHERE status_id IS NOT NULL;
CREATE TRIGGER trg_sync_status_monitor_logs
    BEFORE INSERT OR UPDATE OF status, status_id ON public.monitor_logs
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('monitor_log');

-- ─── 3. system_health_checks ──────────────────────────────────────────────
ALTER TABLE public.system_health_checks ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.system_health_checks DISABLE TRIGGER USER;
UPDATE public.system_health_checks h SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'system_health_check'
  AND es.code = CASE h.status
    WHEN 'ok'       THEN 'ok'
    WHEN 'warning'  THEN 'warning'
    WHEN 'critical' THEN 'critical'
    WHEN 'critique' THEN 'critical'
    ELSE 'ok'
  END
  AND h.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.system_health_checks WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V16 health_checks : % rows', c; END IF; END $$;
ALTER TABLE public.system_health_checks ENABLE TRIGGER USER;
ALTER TABLE public.system_health_checks ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_system_health_checks_status_id ON public.system_health_checks(status_id);
CREATE TRIGGER trg_sync_status_system_health_checks
    BEFORE INSERT OR UPDATE OF status, status_id ON public.system_health_checks
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('system_health_check');

-- ─── 4. team_invitations ──────────────────────────────────────────────────
ALTER TABLE public.team_invitations ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.team_invitations DISABLE TRIGGER USER;
UPDATE public.team_invitations t SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'team_invitation'
  AND es.code = CASE t.status
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'acceptee'   THEN 'acceptee'
    WHEN 'acceptée'   THEN 'acceptee'
    WHEN 'refusee'    THEN 'refusee'
    WHEN 'refusée'    THEN 'refusee'
    WHEN 'expiree'    THEN 'expiree'
    WHEN 'expirée'    THEN 'expiree'
    WHEN 'annulee'    THEN 'annulee'
    WHEN 'annulée'    THEN 'annulee'
    ELSE 'en_attente'
  END
  AND t.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.team_invitations WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V16 team_invitations : % rows', c; END IF; END $$;
ALTER TABLE public.team_invitations ENABLE TRIGGER USER;
ALTER TABLE public.team_invitations ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_team_invitations_status_id ON public.team_invitations(status_id);
CREATE TRIGGER trg_sync_status_team_invitations
    BEFORE INSERT OR UPDATE OF status, status_id ON public.team_invitations
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('team_invitation');
