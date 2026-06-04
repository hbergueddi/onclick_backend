-- ════════════════════════════════════════════════════════════════════
-- V61 — Annulation tardive (Feature #4) + Contestation no-show (Feature #3)
-- ════════════════════════════════════════════════════════════════════
-- Deux ajouts cohérents autour du workflow no_show des réservations :
--
--   Feature #4 — annulation tardive + pénalité no_show
--     • reservations.late_cancellation  : flag « annulation < fenêtre » (le client
--       a prévenu trop tard → traité comme no_show NON contestable, pas de pénalité
--       reversée). Défaut false.
--     • reservations.no_show_marked_at   : horodatage du passage en no_show. Sert de
--       base au calcul des fenêtres de contestation (resto 0-1h, support 1-48h,
--       expiré 48h+). NULL tant que la résa n'est pas marquée absente.
--
--   Feature #3 — contestation no-show (domaine dispute, dans modules/reservation)
--     • Table no_show_disputes : un client conteste un no_show, le resto (phase 0-1h)
--       ou le support (phase 1-48h) tranche. Refs par UUID (pas de FK cross-module
--       ManyToOne — le module reservation reste CLOSED, on garde des UUID plats comme
--       reservation_guests/loyalty).
--
-- Idempotent (IF NOT EXISTS) — re-jouable sans erreur cross-environnement.
-- ════════════════════════════════════════════════════════════════════

-- ─── 1. Feature #4 : colonnes sur reservations ──────────────────────────────
ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS late_cancellation BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS no_show_marked_at TIMESTAMPTZ;

COMMENT ON COLUMN reservations.late_cancellation IS
    'Feature #4 — true si la résa est passée en no_show suite à une annulation tardive (< fenêtre). Rend la résa NON contestable et la pénalité non reversable.';
COMMENT ON COLUMN reservations.no_show_marked_at IS
    'Feature #3/#4 — horodatage du passage status→no_show. Base de calcul des fenêtres de contestation (resto 0-1h, support 1-48h, expiré 48h+).';

-- ─── 2. Feature #3 : table no_show_disputes ─────────────────────────────────
-- Refs par UUID uniquement (reservation_id / client_id / restaurant_id) : le module
-- reservation est CLOSED ; pas de @ManyToOne vers users/restaurants. On indexe sur
-- les colonnes du dashboard (status/phase) + lookup par réservation/client.
CREATE TABLE IF NOT EXISTS no_show_disputes (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id      UUID         NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    client_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    restaurant_id       UUID         NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    status              TEXT         NOT NULL DEFAULT 'pending'
                        CHECK (status IN ('pending', 'accepted', 'refused')),
    escalation_phase    TEXT         NOT NULL DEFAULT 'resto'
                        CHECK (escalation_phase IN ('resto', 'support')),
    reason              TEXT         NOT NULL CHECK (length(trim(reason)) > 0),
    photo_url           TEXT,
    resolution_note     TEXT,
    resolved_by         UUID         REFERENCES users(id) ON DELETE SET NULL,
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_no_show_disputes_reservation
    ON no_show_disputes(reservation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_no_show_disputes_client
    ON no_show_disputes(client_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_no_show_disputes_restaurant_status
    ON no_show_disputes(restaurant_id, status);
CREATE INDEX IF NOT EXISTS idx_no_show_disputes_status_phase
    ON no_show_disputes(status, escalation_phase);

COMMENT ON TABLE no_show_disputes IS
    'Contestation d''un no_show par le client (Feature #3). Phases : resto (0-1h depuis no_show_marked_at) puis support (1-48h). accepted → pénalité no_show reversée (event NoShowDisputeResolvedEvent → listener loyalty) ; refused → pénalité conservée.';
