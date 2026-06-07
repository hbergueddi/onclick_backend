-- ============================================================
-- Gap #2 — Redemption OTP (Snap2Earn)
-- ============================================================
-- Quand un staff tente de convertir (spend) plus de points que le seuil
-- configuré, un code à usage unique est requis. Le code est livré au CLIENT
-- in-app (notification) — il le lit au staff qui le saisit pour valider.
-- Empêche un staff malveillant de vider le solde d'un client sans son
-- consentement explicite pour les grosses conversions.
--
-- Équivalent legacy : RPC request_redemption_otp / verify_redemption_otp +
-- table redemption_otp_requests (4Click 20260417230000_redemption_otp.sql).
-- Spring : la conversion est dénominée en POINTS (pas en MAD) → le seuil
-- actif est otp_required_above_pts. otp_required_above_ratio_pct est conservé
-- pour parité (le ratio MAD dépend de point_value_mad, non modélisé ici).
-- ============================================================

-- 1. Seuils globaux sur gain_rules (per-restaurant rule)
ALTER TABLE public.gain_rules
  ADD COLUMN IF NOT EXISTS otp_required_above_pts INTEGER NOT NULL DEFAULT 200
    CHECK (otp_required_above_pts > 0),
  ADD COLUMN IF NOT EXISTS otp_required_above_ratio_pct NUMERIC(5, 2) NOT NULL DEFAULT 50
    CHECK (otp_required_above_ratio_pct > 0 AND otp_required_above_ratio_pct <= 100);

-- 2. Table des demandes d'OTP
CREATE TABLE IF NOT EXISTS public.redemption_otp_requests (
  id UUID PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,

  client_id UUID NOT NULL,
  restaurant_id UUID NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  staff_id UUID NOT NULL,

  points_requested INTEGER NOT NULL CHECK (points_requested > 0),
  ticket_montant NUMERIC NOT NULL CHECK (ticket_montant >= 0),
  estimated_discount_dh NUMERIC NOT NULL CHECK (estimated_discount_dh >= 0),

  code_hash TEXT NOT NULL,  -- SHA-256 hex du code à 6 chiffres
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'consumed', 'expired', 'cancelled')),

  attempts INTEGER NOT NULL DEFAULT 0,  -- tentatives erronées (anti-bruteforce)
  consumed_at TIMESTAMPTZ,
  consumed_for_ticket_ref TEXT
);

CREATE INDEX IF NOT EXISTS idx_otp_client_pending
  ON public.redemption_otp_requests (client_id, created_at DESC)
  WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS idx_otp_restaurant
  ON public.redemption_otp_requests (restaurant_id, created_at DESC);
