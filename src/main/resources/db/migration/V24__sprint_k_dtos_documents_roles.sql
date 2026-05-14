-- ════════════════════════════════════════════════════════════════════
-- V24 — Sprint K : finalisation migration Supabase → Spring
-- ════════════════════════════════════════════════════════════════════
--
-- Trois chantiers regroupés dans une seule migration cohérente :
--   1. Colonnes manquantes exploitées par le frontend (DTO gaps) sur des
--      tables existantes — restaurants, offers, oneclick_hi_invoices,
--      support_tickets.
--   2. Module documentation interne — app_documents + document_versions
--      (page admin DocumentExport).
--   3. Rôles personnalisés admin — custom_roles (page admin GestionRoles).
--
-- `ddl-auto=validate` : toute colonne ajoutée ici DOIT avoir un champ
-- d'entité correspondant côté Java (et inversement) sous peine d'échec
-- au démarrage.
-- ════════════════════════════════════════════════════════════════════

-- ─── Gap 1a : restaurants — cuisine + max_staff (group_id existe déjà) ───────
ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS cuisine   TEXT,
    ADD COLUMN IF NOT EXISTS max_staff INTEGER CHECK (max_staff IS NULL OR max_staff >= 0);

-- ─── Gap 1b : offers — push_notify + image + segments ───────────────────────
-- NB : la table a déjà `enabled` (toggle actif/inactif). On NE rajoute PAS de
-- colonne is_active redondante — le frontend mappe son `isActive` sur `enabled`.
ALTER TABLE offers
    ADD COLUMN IF NOT EXISTS push_notify BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS image       TEXT,
    ADD COLUMN IF NOT EXISTS segments    TEXT[] DEFAULT '{}';

-- ─── Gap 1c : oneclick_hi_invoices — workflow validation/envoi ──────────────
ALTER TABLE oneclick_hi_invoices
    ADD COLUMN IF NOT EXISTS credit_3pct  NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS validated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS validated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS sent_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS pdf_path     TEXT;

-- ─── Gap 1d : support_tickets — message (restaurant_id + last_reply : V19) ──
ALTER TABLE support_tickets
    ADD COLUMN IF NOT EXISTS message TEXT;

-- ─── Gap 2 : app_documents + document_versions ──────────────────────────────
CREATE TABLE IF NOT EXISTS app_documents (
    id         TEXT PRIMARY KEY,                       -- ex : "plan"
    content    TEXT,
    version    TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS document_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id TEXT NOT NULL REFERENCES app_documents(id) ON DELETE CASCADE,
    version     TEXT,
    content     TEXT,
    notes       TEXT,
    created_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_document_versions_doc
    ON document_versions(document_id, created_at DESC);

-- ─── Gap 3 : custom_roles ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS custom_roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    description TEXT,
    permissions TEXT[] NOT NULL DEFAULT '{}',
    created_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_custom_roles_name ON custom_roles(LOWER(name));

-- ─── Gap 4 : referrals platform-wide list — index de support ────────────────
CREATE INDEX IF NOT EXISTS idx_referrals_created_at ON referrals(created_at DESC);
