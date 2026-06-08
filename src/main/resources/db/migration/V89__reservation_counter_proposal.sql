-- ════════════════════════════════════════════════════════════════════════
-- V89 — Contre-proposition de réservation (créneau proposé par le restaurant)
-- ════════════════════════════════════════════════════════════════════════
-- Le statut applicatif `counter_proposed` existait déjà (reservations.status est un
-- varchar(64), pas une enum DB → aucune contrainte à altérer), MAIS aucun champ ne
-- portait le NOUVEAU créneau proposé → la fonctionnalité était inexploitable côté client.
--
-- On ajoute `proposed_reservation_at` : rempli par le restaurant lors du passage en
-- `counter_proposed`. À l'acceptation du client (counter_proposed → confirmed), cette
-- valeur devient `reservation_at` puis est remise à NULL ; tout statut terminal la nettoie.
-- Idempotent (IF NOT EXISTS) — additif, nullable, aucun backfill nécessaire.

ALTER TABLE reservations ADD COLUMN IF NOT EXISTS proposed_reservation_at timestamptz;

COMMENT ON COLUMN reservations.proposed_reservation_at IS
  'Créneau proposé par le restaurant quand status=counter_proposed ; NULL sinon. '
  'À l''acceptation client (→ confirmed), devient reservation_at.';
