-- ════════════════════════════════════════════════════════════════════
-- V17 — Sprint G.2.1 : workflow ReservationGuest (port legacy → enterprise)
-- ════════════════════════════════════════════════════════════════════
-- Le schéma V4 de reservation_guests était minimal (id, reservation_id,
-- guest_user_id, guest_name, created_at). Le frontend Pocket (OneClickReserve
-- + OneClickSpotlight) attend en plus :
--   • guest_phone   — pour invitations par téléphone (cas user pas dans OneClick)
--   • invited_by    — auditer qui a invité (organizer ou co-invité)
--   • status        — workflow invitation (invited → accepted | refused | cancelled)
--
-- Migration additive — aucune donnée existante (table vide en greenfield).
-- ════════════════════════════════════════════════════════════════════

-- ─── 1. Nouvelles colonnes ────────────────────────────────────────────
ALTER TABLE reservation_guests
    ADD COLUMN guest_phone TEXT,
    ADD COLUMN invited_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN status      TEXT NOT NULL DEFAULT 'linked'
        CHECK (status IN ('linked', 'invited', 'accepted', 'refused', 'cancelled')),
    ADD COLUMN seen_by_host BOOLEAN NOT NULL DEFAULT false;

-- ─── 2. Contrainte métier : au moins un identifiant (legacy logic) ───
-- Soit guest_user_id (user OneClick existant), soit guest_phone (à inviter),
-- soit guest_name (placeholder). Les 3 NULL = invalide.
ALTER TABLE reservation_guests
    ADD CONSTRAINT reservation_guests_identity_check
    CHECK (
        guest_user_id IS NOT NULL OR
        guest_phone IS NOT NULL OR
        guest_name IS NOT NULL
    );

-- ─── 3. Anti-doublon : 1 invité par téléphone par réservation ────────
-- Permet le re-invite si refusé/annulé (UPDATE plutôt qu'INSERT).
CREATE UNIQUE INDEX idx_reservation_guests_phone_unique
    ON reservation_guests(reservation_id, guest_phone)
    WHERE guest_phone IS NOT NULL;

CREATE UNIQUE INDEX idx_reservation_guests_user_unique
    ON reservation_guests(reservation_id, guest_user_id)
    WHERE guest_user_id IS NOT NULL;

-- ─── 4. Index pour les lookups frontend (Pocket invité) ──────────────
CREATE INDEX idx_reservation_guests_status ON reservation_guests(status)
    WHERE status IN ('invited', 'linked');

CREATE INDEX idx_reservation_guests_invited_by ON reservation_guests(invited_by);

COMMENT ON COLUMN reservation_guests.status IS
    'Workflow invitation : linked (auto-attaché) | invited (notif envoyée) | accepted | refused | cancelled';
COMMENT ON COLUMN reservation_guests.seen_by_host IS
    'true quand l''organisateur a consulté la réponse du guest (notification UI)';
