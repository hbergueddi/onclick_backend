-- ============================================================
-- Gap #4 — Resend bounce handling + suppression list
-- ============================================================
-- Table de tracking des bounces emails Resend (transactionnels), alimentée par
-- le webhook /api/email/webhooks/resend (events email.bounced / email.complained).
-- Consultée avant chaque envoi (suppression check) pour ne pas retry les bounces
-- permanents (protège la réputation du domaine / le bounce rate).
--
-- Équivalent legacy : 4Click 20260429160000_email_bounces.sql (table +
-- record_email_bounce / is_email_suppressed + EF resend-webhooks).
-- Tenant-agnostic : filtre purement par adresse email (PII). Pas de RBAC resource
-- dédiée — la lecture admin est gardée par VIEW:USERS (admin-only).
-- ============================================================

CREATE TABLE IF NOT EXISTS public.email_bounces (
  id              UUID         PRIMARY KEY,
  email           TEXT         NOT NULL UNIQUE,                          -- normalisé lowercase
  bounce_type     TEXT         NOT NULL CHECK (bounce_type IN ('permanent', 'transient', 'complaint')),
  bounce_reason   TEXT,
  is_suppressed   BOOLEAN      NOT NULL DEFAULT false,                   -- true si permanent/complaint → skip
  last_bounced_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  bounce_count    INTEGER      NOT NULL DEFAULT 1,
  source_ef       TEXT,                                                  -- contexte d'envoi (info)
  raw_event       JSONB,                                                 -- payload Resend complet (debug)
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Lookup ultra-rapide pre-send (cas le plus chaud).
CREATE INDEX IF NOT EXISTS idx_email_bounces_email_suppressed
  ON public.email_bounces (email) WHERE is_suppressed = true;

-- Index général pour stats/admin.
CREATE INDEX IF NOT EXISTS idx_email_bounces_last_bounced
  ON public.email_bounces (last_bounced_at DESC);
