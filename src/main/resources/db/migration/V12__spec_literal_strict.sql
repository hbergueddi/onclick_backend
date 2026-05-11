-- ============================================================================
-- V12 — Phase 4 spec littéral strict (§16 + §17 + §18)
-- ============================================================================
-- Référence : oneclick_architecture_enterprise_optimized.md
--   §16 — Audit standards : ajouter created_by/updated_by/deleted_at sur
--         toutes les tables principales (était partiel sur 25+ tables)
--   §17 — Multi-tenant : tenant_id sur les tables business sub-entités utiles
--         (les principales sont déjà OK depuis V9)
--   §18 — Index SQL : ajouter INDEX(reservation_at) standalone
--
-- ============================================================================

-- ─── §18 #1 — INDEX(reservation_at) standalone ─────────────────────────────
-- Sert : "toutes les résas demain", queries sans filtre client/restaurant
CREATE INDEX IF NOT EXISTS idx_reservations_at
    ON reservations(reservation_at)
    WHERE deleted_at IS NULL;

-- ─── §16 — Compléter audit fields sur tables business principales ──────────
-- Tables qui ont actuellement 2 ou 3 colonnes audit → on les passe à 5.
-- Skip les tables système (logs, tokens, junction) qui n'en ont pas besoin.

-- Helpers : on regroupe les ALTERs en batches pour la lisibilité

-- ── Niveau 1 : tables à 3 cols → ajouter deleted_at + updated_by ──────────
ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE file_attachments
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE notification_campaigns
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE payment_methods
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE resource_bookings
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE restaurant_staffs
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

-- ── Niveau 2 : tables à 2 cols → ajouter deleted_at + created_by + updated_by ──
-- (déjà has created_at + updated_at, manque les 3 autres)
ALTER TABLE booking_rules
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE business_hours
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE company_settings
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE loyalty_accounts
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE loyalty_rules
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE redemptions
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE referrals
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE resource_pricings
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE restaurant_services
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE restaurant_tables
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE restaurant_zones
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE support_tickets
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE tenant_brandings
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE tenant_features
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE tiers
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE wallet_transactions
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

-- ── Niveau 3 : friendships + event_participations + device_tokens ──────────
ALTER TABLE friendships
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE event_participations
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE device_tokens
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

-- ─── §17 — tenant_id sur sub-entités utiles ────────────────────────────────
-- Justification : ces tables reçoivent des INSERT volumineux par flow business
-- (transactions de fidélité, lignes de facture, etc.). Avoir tenant_id direct
-- évite un JOIN systématique sur le parent dans les queries reporting.

-- loyalty_transactions ← loyalty_accounts.tenant_id
ALTER TABLE loyalty_transactions ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE loyalty_transactions lt SET tenant_id = la.tenant_id
    FROM loyalty_accounts la WHERE la.id = lt.account_id AND lt.tenant_id IS NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name='loyalty_transactions' AND constraint_name='loyalty_transactions_tenant_id_fkey') THEN
        ALTER TABLE loyalty_transactions ADD CONSTRAINT loyalty_transactions_tenant_id_fkey
            FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_loyalty_transactions_tenant ON loyalty_transactions(tenant_id);

-- redemptions ← loyalty_accounts.tenant_id
ALTER TABLE redemptions ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE redemptions r SET tenant_id = la.tenant_id
    FROM loyalty_accounts la WHERE la.id = r.account_id AND r.tenant_id IS NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name='redemptions' AND constraint_name='redemptions_tenant_id_fkey') THEN
        ALTER TABLE redemptions ADD CONSTRAINT redemptions_tenant_id_fkey
            FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_redemptions_tenant ON redemptions(tenant_id);

-- invoices ← restaurants.tenant_id
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE invoices i SET tenant_id = r.tenant_id
    FROM restaurants r WHERE r.id = i.restaurant_id AND i.tenant_id IS NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name='invoices' AND constraint_name='invoices_tenant_id_fkey') THEN
        ALTER TABLE invoices ADD CONSTRAINT invoices_tenant_id_fkey
            FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_invoices_tenant ON invoices(tenant_id);

-- wallet_transactions ← restaurants.tenant_id
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE wallet_transactions wt SET tenant_id = r.tenant_id
    FROM restaurants r WHERE r.id = wt.restaurant_id AND wt.tenant_id IS NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name='wallet_transactions' AND constraint_name='wallet_transactions_tenant_id_fkey') THEN
        ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transactions_tenant_id_fkey
            FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_tenant ON wallet_transactions(tenant_id);

-- resource_bookings ← resources.tenant_id
ALTER TABLE resource_bookings ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE resource_bookings rb SET tenant_id = res.tenant_id
    FROM resources res WHERE res.id = rb.resource_id AND rb.tenant_id IS NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name='resource_bookings' AND constraint_name='resource_bookings_tenant_id_fkey') THEN
        ALTER TABLE resource_bookings ADD CONSTRAINT resource_bookings_tenant_id_fkey
            FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_resource_bookings_tenant ON resource_bookings(tenant_id);

-- event_participations ← events.tenant_id
ALTER TABLE event_participations ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE event_participations ep SET tenant_id = e.tenant_id
    FROM events e WHERE e.id = ep.event_id AND ep.tenant_id IS NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name='event_participations' AND constraint_name='event_participations_tenant_id_fkey') THEN
        ALTER TABLE event_participations ADD CONSTRAINT event_participations_tenant_id_fkey
            FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_event_participations_tenant ON event_participations(tenant_id);

-- ticket_messages ← support_tickets.tenant_id
ALTER TABLE ticket_messages ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE ticket_messages tm SET tenant_id = st.tenant_id
    FROM support_tickets st WHERE st.id = tm.ticket_id AND tm.tenant_id IS NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name='ticket_messages' AND constraint_name='ticket_messages_tenant_id_fkey') THEN
        ALTER TABLE ticket_messages ADD CONSTRAINT ticket_messages_tenant_id_fkey
            FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_ticket_messages_tenant ON ticket_messages(tenant_id);

COMMENT ON INDEX idx_reservations_at IS
    'Phase 4 §18 #1 spec senior dev — INDEX(reservation_at) standalone';
