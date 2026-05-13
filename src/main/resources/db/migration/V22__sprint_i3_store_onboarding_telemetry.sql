-- ════════════════════════════════════════════════════════════════════
-- V22 — Sprint I.3 : tables Store onboarding + Monitor telemetry
-- ════════════════════════════════════════════════════════════════════
--
-- Tables ajoutées pour porter les EFs Supabase restantes :
--   - store_onboarding_requests (workflow demandes inscription Store)
--   - monitor_logs (ingestion télémétrie batch app mobile)
-- ════════════════════════════════════════════════════════════════════

-- ─── store_onboarding_requests ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS store_onboarding_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    -- Informations restaurant
    restaurant_name VARCHAR(255) NOT NULL,
    cuisine VARCHAR(64),
    city VARCHAR(64),
    address TEXT,
    phone VARCHAR(32),
    -- Owner candidat
    owner_first_name VARCHAR(128) NOT NULL,
    owner_last_name VARCHAR(128) NOT NULL,
    owner_email VARCHAR(255) NOT NULL,
    owner_phone VARCHAR(32),
    -- Workflow
    status VARCHAR(32) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected','onboarded')),
    rejection_reason TEXT,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    decision_email_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_store_onboarding_status ON store_onboarding_requests(status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_store_onboarding_email ON store_onboarding_requests(owner_email);

-- ─── monitor_logs ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS monitor_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    tenant_id UUID,
    app_id VARCHAR(64),
    event_type VARCHAR(64) NOT NULL,
    event_data JSONB,
    platform VARCHAR(32),  -- ios / android / web
    app_version VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_monitor_logs_user_event ON monitor_logs(user_id, event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_monitor_logs_event_type ON monitor_logs(event_type, created_at DESC);
