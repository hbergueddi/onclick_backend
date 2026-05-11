-- ============================================================================
-- V9 — Phase 3 §17 spec senior dev : tenant_id sur tables métier
-- ============================================================================
-- Référence : oneclick_architecture_enterprise_optimized.md §17
--   "Ajouter dans toutes les tables métier nécessaires"
--
-- 7 tables enrichies avec tenant_id explicite (dérivé du parent FK) :
--   contracts        → via restaurant_id
--   loyalty_accounts → via restaurant_id
--   notifications    → via recipient_user_id
--   offers           → via restaurant_id
--   payments         → via user_id
--   posts            → via author_id
--   support_tickets  → via opened_by
--
-- Stratégie en 3 étapes pour chaque table :
--   1. ADD COLUMN tenant_id (nullable initial)
--   2. UPDATE populate depuis le parent
--   3. ALTER COLUMN NOT NULL + ADD FK + index
--
-- Bénéfice : queries multi-tenant utilisent un index direct (sans JOIN).
-- ============================================================================

-- ─── 1. contracts ──────────────────────────────────────────────────────────
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE contracts c SET tenant_id = r.tenant_id
    FROM restaurants r WHERE r.id = c.restaurant_id AND c.tenant_id IS NULL;
ALTER TABLE contracts ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE contracts ADD CONSTRAINT contracts_tenant_id_fkey
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_contracts_tenant ON contracts(tenant_id);

-- ─── 2. loyalty_accounts ───────────────────────────────────────────────────
ALTER TABLE loyalty_accounts ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE loyalty_accounts la SET tenant_id = r.tenant_id
    FROM restaurants r WHERE r.id = la.restaurant_id AND la.tenant_id IS NULL;
ALTER TABLE loyalty_accounts ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE loyalty_accounts ADD CONSTRAINT loyalty_accounts_tenant_id_fkey
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_loyalty_accounts_tenant ON loyalty_accounts(tenant_id);

-- ─── 3. notifications ──────────────────────────────────────────────────────
-- Note : 40k rows, l'UPDATE peut prendre 2-3s
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE notifications n SET tenant_id = u.tenant_id
    FROM users u WHERE u.id = n.recipient_user_id AND n.tenant_id IS NULL;
-- Certains users n'ont pas de tenant_id (admins plateforme) → fallback OneClick tenant
UPDATE notifications SET tenant_id = '00000000-0000-0000-0000-000000000001'
    WHERE tenant_id IS NULL;
ALTER TABLE notifications ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE notifications ADD CONSTRAINT notifications_tenant_id_fkey
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_notifications_tenant_created
    ON notifications(tenant_id, created_at DESC);

-- ─── 4. offers ─────────────────────────────────────────────────────────────
ALTER TABLE offers ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE offers o SET tenant_id = r.tenant_id
    FROM restaurants r WHERE r.id = o.restaurant_id AND o.tenant_id IS NULL;
ALTER TABLE offers ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE offers ADD CONSTRAINT offers_tenant_id_fkey
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_offers_tenant ON offers(tenant_id);

-- ─── 5. payments ──────────────────────────────────────────────────────────
-- 0 rows actuellement, mais on prépare la structure
ALTER TABLE payments ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE payments p SET tenant_id = u.tenant_id
    FROM users u WHERE u.id = p.user_id AND p.tenant_id IS NULL;
UPDATE payments SET tenant_id = '00000000-0000-0000-0000-000000000001'
    WHERE tenant_id IS NULL;
ALTER TABLE payments ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE payments ADD CONSTRAINT payments_tenant_id_fkey
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_payments_tenant ON payments(tenant_id);

-- ─── 6. posts ─────────────────────────────────────────────────────────────
-- 0 rows actuellement
ALTER TABLE posts ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE posts p SET tenant_id = u.tenant_id
    FROM users u WHERE u.id = p.author_id AND p.tenant_id IS NULL;
UPDATE posts SET tenant_id = '00000000-0000-0000-0000-000000000001'
    WHERE tenant_id IS NULL;
ALTER TABLE posts ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE posts ADD CONSTRAINT posts_tenant_id_fkey
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_posts_tenant_created
    ON posts(tenant_id, created_at DESC);

-- ─── 7. support_tickets ────────────────────────────────────────────────────
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE support_tickets st SET tenant_id = u.tenant_id
    FROM users u WHERE u.id = st.opened_by AND st.tenant_id IS NULL;
UPDATE support_tickets SET tenant_id = '00000000-0000-0000-0000-000000000001'
    WHERE tenant_id IS NULL;
ALTER TABLE support_tickets ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_tenant_id_fkey
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_support_tickets_tenant ON support_tickets(tenant_id);
