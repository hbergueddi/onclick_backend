-- V13 — Migration reservation/ (Friendship + ReservationGuest). Pattern V9.
-- NB : table reservations elle-même est un enum Postgres natif → Phase 4.

-- ─── 1. friendships ────────────────────────────────────────────────────────
ALTER TABLE public.friendships ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.friendships DISABLE TRIGGER USER;
UPDATE public.friendships f SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'friendship'
  AND es.code = CASE f.status
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
  AND f.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.friendships WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V13 friendships : % rows', c; END IF; END $$;
ALTER TABLE public.friendships ENABLE TRIGGER USER;
ALTER TABLE public.friendships ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_friendships_status_id ON public.friendships(status_id);
CREATE TRIGGER trg_sync_status_friendships
    BEFORE INSERT OR UPDATE OF status, status_id ON public.friendships
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('friendship');

-- ─── 2. reservation_guests ─────────────────────────────────────────────────
ALTER TABLE public.reservation_guests ADD COLUMN IF NOT EXISTS status_id UUID
    REFERENCES public.entity_statuses(id);
ALTER TABLE public.reservation_guests DISABLE TRIGGER USER;
UPDATE public.reservation_guests g SET status_id = es.id
FROM public.entity_statuses es
WHERE es.entity_type = 'reservation_guest'
  AND es.code = CASE g.status
    WHEN 'en_attente' THEN 'en_attente'
    WHEN 'lie'        THEN 'lie'
    WHEN 'lié'        THEN 'lie'
    WHEN 'accepte'    THEN 'accepte'
    WHEN 'accepté'    THEN 'accepte'
    WHEN 'refuse'     THEN 'refuse'
    WHEN 'refusé'     THEN 'refuse'
    WHEN 'annule'     THEN 'annule'
    WHEN 'annulée'    THEN 'annule'
    ELSE 'en_attente'
  END
  AND g.status_id IS NULL;
DO $$ DECLARE c INT; BEGIN SELECT count(*) INTO c FROM public.reservation_guests WHERE status_id IS NULL;
  IF c > 0 THEN RAISE EXCEPTION 'V13 reservation_guests : % rows', c; END IF; END $$;
ALTER TABLE public.reservation_guests ENABLE TRIGGER USER;
ALTER TABLE public.reservation_guests ALTER COLUMN status_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_reservation_guests_status_id ON public.reservation_guests(status_id);
CREATE TRIGGER trg_sync_status_reservation_guests
    BEFORE INSERT OR UPDATE OF status, status_id ON public.reservation_guests
    FOR EACH ROW EXECUTE FUNCTION public.sync_entity_status_columns('reservation_guest');
