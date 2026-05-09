-- ============================================================================
-- V9 — Migration restaurant/ status text → status_id FK + trigger sync
-- ============================================================================
--
-- IMPORTANT — stratégie pour les tables avec dépendances legacy :
--
-- Plusieurs tables (restaurants notamment) ont des views et/ou triggers
-- legacy qui référencent la colonne `status` text. Un DROP COLUMN cascade
-- détruirait :
--   * v_restaurants_core, v_restaurants_with_group
--   * trg_activate_restaurant_referral (référence NEW.status='actif')
--
-- → On garde la colonne `status` text en parallèle de `status_id` UUID,
-- avec un trigger de synchronisation bidirectionnelle. Phase 4 cleanup
-- ultérieure : refacto views/triggers + DROP COLUMN.
--
-- ATTENTION triggers business :
--   * `check_max_active_staff()` plante si UPDATE de toutes les rows
--     d'un resto > 20 staff actif. Solution : DISABLE TRIGGER USER
--     pendant le UPDATE backfill, puis ENABLE après.
-- ============================================================================

-- ─── 0. Fonction générique de sync status text ↔ status_id ─────────────────
CREATE OR REPLACE FUNCTION public.sync_entity_status_columns()
RETURNS TRIGGER AS $$
DECLARE
    ent_type TEXT := TG_ARGV[0];
    resolved_id UUID;
    resolved_code TEXT;
BEGIN
    -- Cas 1 : status_id set/changé → backfill status text
    IF NEW.status_id IS NOT NULL
       AND (TG_OP = 'INSERT' OR NEW.status_id IS DISTINCT FROM OLD.status_id) THEN
        SELECT code INTO resolved_code
        FROM public.entity_statuses WHERE id = NEW.status_id;
        IF resolved_code IS NULL THEN
            RAISE EXCEPTION 'Unknown status_id % for table %', NEW.status_id, TG_TABLE_NAME;
        END IF;
        NEW.status := resolved_code;
        RETURN NEW;
    END IF;

    -- Cas 2 : status text set/changé → backfill status_id
    IF NEW.status IS NOT NULL
       AND (TG_OP = 'INSERT' OR NEW.status IS DISTINCT FROM OLD.status) THEN
        SELECT id INTO resolved_id
        FROM public.entity_statuses
        WHERE entity_type = ent_type AND code = NEW.status;
        IF resolved_id IS NULL THEN
            SELECT id INTO resolved_id
            FROM public.entity_statuses
            WHERE entity_type = ent_type
              AND (label_fr = NEW.status OR code = NEW.status);
        END IF;
        IF resolved_id IS NULL THEN
            RAISE EXCEPTION 'Unknown status code ''%'' for entity_type %', NEW.status, ent_type;
        END IF;
        NEW.status_id := resolved_id;
        RETURN NEW;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION public.sync_entity_status_columns IS
'Sync bidirectionnelle status text ↔ status_id pour la migration progressive vers entity_statuses. Phase 4 = drop la colonne status text + cette fonction.';

-- ============================================================================
-- Helper macro : pattern unifié par table — DISABLE TRIGGER USER pendant
-- backfill pour ne pas déclencher les triggers métier (check_max_active_staff
-- etc.).
-- ============================================================================

-- ─── 1. restaurants ─────────────────────────────────────────────────────────
ALTER TABLE public.restaurants ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);

ALTER TABLE public.restaurants DISABLE TRIGGER USER;

UPDATE public.restaurants r SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'restaurant'
  AND es.code = CASE r.status
    WHEN 'actif'    THEN 'actif'
    WHEN 'inactif'  THEN 'inactif'
    WHEN 'suspendu' THEN 'suspendu'
    WHEN 'prospect' THEN 'prospect'
    WHEN 'archive'  THEN 'archive'
    WHEN 'archivé'  THEN 'archive'
    ELSE 'actif'
  END
  AND r.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.restaurants WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V9 restaurants : % rows sans status_id', c; END IF;
END $$;

ALTER TABLE public.restaurants ENABLE TRIGGER USER;
ALTER TABLE public.restaurants ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_restaurants_status_id ON public.restaurants(status_id);
CREATE TRIGGER trg_sync_status_restaurants
    BEFORE INSERT OR UPDATE OF status, status_id ON public.restaurants
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('restaurant');

-- ─── 2. restaurant_media ────────────────────────────────────────────────────
ALTER TABLE public.restaurant_media ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);

ALTER TABLE public.restaurant_media DISABLE TRIGGER USER;

UPDATE public.restaurant_media m SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'restaurant_media'
  AND es.code = CASE m.status
    WHEN 'actif'   THEN 'actif'
    WHEN 'archive' THEN 'archive'
    WHEN 'archivé' THEN 'archive'
    ELSE 'actif'
  END
  AND m.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.restaurant_media WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V9 restaurant_media : % rows sans status_id', c; END IF;
END $$;

ALTER TABLE public.restaurant_media ENABLE TRIGGER USER;
ALTER TABLE public.restaurant_media ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_restaurant_media_status_id ON public.restaurant_media(status_id);
CREATE TRIGGER trg_sync_status_restaurant_media
    BEFORE INSERT OR UPDATE OF status, status_id ON public.restaurant_media
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('restaurant_media');

-- ─── 3. restaurant_restitutions ─────────────────────────────────────────────
ALTER TABLE public.restaurant_restitutions ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);

ALTER TABLE public.restaurant_restitutions DISABLE TRIGGER USER;

UPDATE public.restaurant_restitutions r SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'restaurant_restitution'
  AND es.code = CASE r.status
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'traitee'    THEN 'traitee'
    WHEN 'traitée'    THEN 'traitee'
    WHEN 'annulee'    THEN 'annulee'
    WHEN 'annulée'    THEN 'annulee'
    ELSE 'en_attente'
  END
  AND r.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.restaurant_restitutions WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V9 restaurant_restitutions : % rows sans status_id', c; END IF;
END $$;

ALTER TABLE public.restaurant_restitutions ENABLE TRIGGER USER;
ALTER TABLE public.restaurant_restitutions ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_restaurant_restitutions_status_id ON public.restaurant_restitutions(status_id);
CREATE TRIGGER trg_sync_status_restaurant_restitutions
    BEFORE INSERT OR UPDATE OF status, status_id ON public.restaurant_restitutions
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('restaurant_restitution');

-- ─── 4. restaurant_services ─────────────────────────────────────────────────
ALTER TABLE public.restaurant_services ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);

ALTER TABLE public.restaurant_services DISABLE TRIGGER USER;

UPDATE public.restaurant_services s SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'restaurant_service'
  AND es.code = CASE s.status
    WHEN 'actif'   THEN 'actif'
    WHEN 'inactif' THEN 'inactif'
    WHEN 'archive' THEN 'archive'
    WHEN 'archivé' THEN 'archive'
    ELSE 'actif'
  END
  AND s.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.restaurant_services WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V9 restaurant_services : % rows sans status_id', c; END IF;
END $$;

ALTER TABLE public.restaurant_services ENABLE TRIGGER USER;
ALTER TABLE public.restaurant_services ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_restaurant_services_status_id ON public.restaurant_services(status_id);
CREATE TRIGGER trg_sync_status_restaurant_services
    BEFORE INSERT OR UPDATE OF status, status_id ON public.restaurant_services
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('restaurant_service');

-- ─── 5. restaurant_staff (table avec trigger métier check_max_active_staff) ─
ALTER TABLE public.restaurant_staff ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);

ALTER TABLE public.restaurant_staff DISABLE TRIGGER USER;

UPDATE public.restaurant_staff s SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'restaurant_staff'
  AND es.code = CASE s.status
    WHEN 'actif'     THEN 'actif'
    WHEN 'desactive' THEN 'desactive'
    WHEN 'désactivé' THEN 'desactive'
    ELSE 'actif'
  END
  AND s.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.restaurant_staff WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V9 restaurant_staff : % rows sans status_id', c; END IF;
END $$;

ALTER TABLE public.restaurant_staff ENABLE TRIGGER USER;
ALTER TABLE public.restaurant_staff ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_restaurant_staff_status_id ON public.restaurant_staff(status_id);
CREATE TRIGGER trg_sync_status_restaurant_staff
    BEFORE INSERT OR UPDATE OF status, status_id ON public.restaurant_staff
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('restaurant_staff');

-- ─── 6. restaurant_tables ───────────────────────────────────────────────────
ALTER TABLE public.restaurant_tables ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);

ALTER TABLE public.restaurant_tables DISABLE TRIGGER USER;

UPDATE public.restaurant_tables t SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'restaurant_table'
  AND es.code = CASE t.status
    WHEN 'actif'   THEN 'actif'
    WHEN 'inactif' THEN 'inactif'
    WHEN 'archive' THEN 'archive'
    WHEN 'archivé' THEN 'archive'
    ELSE 'actif'
  END
  AND t.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.restaurant_tables WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V9 restaurant_tables : % rows sans status_id', c; END IF;
END $$;

ALTER TABLE public.restaurant_tables ENABLE TRIGGER USER;
ALTER TABLE public.restaurant_tables ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_restaurant_tables_status_id ON public.restaurant_tables(status_id);
CREATE TRIGGER trg_sync_status_restaurant_tables
    BEFORE INSERT OR UPDATE OF status, status_id ON public.restaurant_tables
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('restaurant_table');

-- ─── 7. restaurant_zones ────────────────────────────────────────────────────
ALTER TABLE public.restaurant_zones ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);

ALTER TABLE public.restaurant_zones DISABLE TRIGGER USER;

UPDATE public.restaurant_zones z SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'restaurant_zone'
  AND es.code = CASE z.status
    WHEN 'actif'   THEN 'actif'
    WHEN 'inactif' THEN 'inactif'
    WHEN 'archive' THEN 'archive'
    WHEN 'archivé' THEN 'archive'
    ELSE 'actif'
  END
  AND z.status_id IS NULL;

DO $$ DECLARE c INT; BEGIN
    SELECT count(*) INTO c FROM public.restaurant_zones WHERE status_id IS NULL;
    IF c > 0 THEN RAISE EXCEPTION 'V9 restaurant_zones : % rows sans status_id', c; END IF;
END $$;

ALTER TABLE public.restaurant_zones ENABLE TRIGGER USER;
ALTER TABLE public.restaurant_zones ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_restaurant_zones_status_id ON public.restaurant_zones(status_id);
CREATE TRIGGER trg_sync_status_restaurant_zones
    BEFORE INSERT OR UPDATE OF status, status_id ON public.restaurant_zones
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('restaurant_zone');
