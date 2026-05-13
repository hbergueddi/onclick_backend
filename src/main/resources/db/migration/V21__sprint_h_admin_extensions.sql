-- ════════════════════════════════════════════════════════════════════
-- V21 — Sprint H : extensions admin + whitelabel pour atteindre 100%
-- ════════════════════════════════════════════════════════════════════
--
-- Tables ajoutées pour combler le gap legacy Supabase :
--   - client_ratings              (système de notation 0-5)
--   - elite_applications          (demandes accès Elite club)
--   - ai_usage                    (rate limit AI par user/jour)
--   - restaurant_groups           (Galaxy concept — groupes de restos)
--   - promo_notification_requests (workflow admin approval push promo)
--   - explore_featured            (curation manuelle restos featured)
--   - quota_change_logs           (audit changement quotas)
--   - system_health_checks        (heartbeats jobs)
--   - system_alert_rules          (config admin alertes)
--   - system_alerts               (log alertes déclenchées)
--   - restaurant_tier_config      (config tier par resto)
--   - restaurant_tier_status      (statut tier par resto)
--   - restaurant_restitutions     (refund tracking points)
--   - offer_impressions           (tracking vues offres)
--   - oneclick_hi_invoices        (factures whitelabel HR)
--
-- Toutes ces tables sont versionnées via Flyway et tracent leur création.
-- ════════════════════════════════════════════════════════════════════

-- ─── client_ratings ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS client_ratings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reservation_id UUID REFERENCES reservations(id) ON DELETE SET NULL,
    rating NUMERIC(2,1) NOT NULL DEFAULT 5.0 CHECK (rating BETWEEN 0 AND 5),
    visible_rating NUMERIC(2,1) NOT NULL DEFAULT 5.0,
    pending_rating NUMERIC(2,1),
    reason TEXT,
    delta NUMERIC(2,1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_client_ratings_user ON client_ratings(user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_client_ratings_created ON client_ratings(created_at DESC);

-- ─── elite_applications ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS elite_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
    motivation TEXT,
    referrer_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    UNIQUE (user_id, status) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX IF NOT EXISTS idx_elite_applications_status ON elite_applications(status) WHERE deleted_at IS NULL;

-- ─── ai_usage ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    prompt_count INTEGER NOT NULL DEFAULT 0,
    last_prompt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id)
);
CREATE INDEX IF NOT EXISTS idx_ai_usage_user ON ai_usage(user_id);

-- ─── restaurant_groups ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS restaurant_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id UUID REFERENCES users(id) ON DELETE SET NULL,
    logo_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'actif',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_restaurant_groups_owner ON restaurant_groups(owner_id) WHERE deleted_at IS NULL;

-- Lien groupe ↔ restaurant (optionnel via colonne sur restaurants)
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES restaurant_groups(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_restaurants_group_id ON restaurants(group_id) WHERE group_id IS NOT NULL;

-- ─── promo_notification_requests ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS promo_notification_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    restaurant_id UUID REFERENCES restaurants(id) ON DELETE CASCADE,
    offer_id UUID REFERENCES offers(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    segment VARCHAR(64) NOT NULL DEFAULT 'all',
    status VARCHAR(32) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected','sent')),
    requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    push_sent_at TIMESTAMPTZ,
    push_sent_count INTEGER DEFAULT 0,
    push_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_promo_notif_requests_status ON promo_notification_requests(status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_promo_notif_requests_resto ON promo_notification_requests(restaurant_id);

-- ─── explore_featured ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS explore_featured (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    rank INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT true,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id)
);
CREATE INDEX IF NOT EXISTS idx_explore_featured_rank ON explore_featured(rank) WHERE enabled = true;

-- ─── quota_change_logs ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS quota_change_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    restaurant_id UUID REFERENCES restaurants(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    quota_type VARCHAR(64) NOT NULL,
    old_value INTEGER,
    new_value INTEGER,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_quota_logs_resto ON quota_change_logs(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_quota_logs_created ON quota_change_logs(created_at DESC);

-- ─── system_health_checks ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS system_health_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    component VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    latency_ms INTEGER,
    error_message TEXT,
    metadata JSONB,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_health_checks_component ON system_health_checks(component, checked_at DESC);

-- ─── system_alert_rules ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS system_alert_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    condition_expr TEXT NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'warning',
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── system_alerts ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS system_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID REFERENCES system_alert_rules(id) ON DELETE SET NULL,
    severity VARCHAR(32) NOT NULL,
    message TEXT NOT NULL,
    context JSONB,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_system_alerts_unack ON system_alerts(created_at DESC) WHERE acknowledged_at IS NULL;

-- ─── restaurant_tier_config + restaurant_tier_status ─────────────────
CREATE TABLE IF NOT EXISTS restaurant_tier_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    tier_name VARCHAR(64) NOT NULL,
    points_required INTEGER NOT NULL DEFAULT 0,
    benefits JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id, tier_name)
);

CREATE TABLE IF NOT EXISTS restaurant_tier_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    current_tier VARCHAR(64) NOT NULL DEFAULT 'Standard',
    points_earned INTEGER NOT NULL DEFAULT 0,
    last_evaluated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id)
);

-- ─── restaurant_restitutions ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS restaurant_restitutions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    amount NUMERIC(12,2) NOT NULL,
    points INTEGER NOT NULL DEFAULT 0,
    reason TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected','paid')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_restitutions_resto ON restaurant_restitutions(restaurant_id);

-- ─── offer_impressions ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS offer_impressions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_id UUID NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    impression_type VARCHAR(32) NOT NULL DEFAULT 'view',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_offer_impressions_offer ON offer_impressions(offer_id, created_at DESC);

-- ─── oneclick_hi_invoices (whitelabel HR/payroll) ────────────────────
CREATE TABLE IF NOT EXISTS oneclick_hi_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    restaurant_id UUID REFERENCES restaurants(id) ON DELETE SET NULL,
    invoice_number VARCHAR(64) UNIQUE,
    period_month VARCHAR(10) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    vat_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','sent','paid','overdue')),
    pdf_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_hi_invoices_resto ON oneclick_hi_invoices(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_hi_invoices_period ON oneclick_hi_invoices(period_month DESC);
