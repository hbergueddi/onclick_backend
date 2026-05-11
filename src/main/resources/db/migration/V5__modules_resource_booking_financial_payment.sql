-- ════════════════════════════════════════════════════════════════════
-- V5 — modules/resource_booking (§10) + modules/financial (§11) + modules/payment (§12)
-- ════════════════════════════════════════════════════════════════════
-- 16 tables : 4 resource_booking + 4 financial + 4 payment + 4 helpers
-- ════════════════════════════════════════════════════════════════════

-- ─── modules/resource_booking §10 ────────────────────────────────────
-- Modèle générique : remplace bookable_resources + resource_bookings +
-- loyalty_punch_cards PCC-specific. Multi-tenant by design.

CREATE TABLE resources (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    resource_type   text NOT NULL,
    name            text NOT NULL,
    description     text,
    capacity        integer,
    enabled         boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL,
    updated_by      uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_resources_tenant_type ON resources(tenant_id, resource_type) WHERE deleted_at IS NULL AND enabled = true;
COMMENT ON TABLE resources IS 'Ressources bookables génériques (padel, spa, golf, coiffeur, gym...) §10';

CREATE TABLE resource_pricings (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id     uuid NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    name            text NOT NULL,
    price           numeric(12, 2) NOT NULL CHECK (price >= 0),
    duration_minutes integer,
    enabled         boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_resource_pricings_resource ON resource_pricings(resource_id) WHERE enabled = true;
COMMENT ON TABLE resource_pricings IS 'Grilles tarifaires par ressource (1h padel, 30 min coiffeur, ...)';

CREATE TABLE resource_bookings (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id     uuid NOT NULL REFERENCES resources(id) ON DELETE RESTRICT,
    organizer_id    uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    pricing_id      uuid REFERENCES resource_pricings(id) ON DELETE SET NULL,
    start_at        timestamptz NOT NULL,
    end_at          timestamptz NOT NULL,
    status          text NOT NULL DEFAULT 'confirmed' CHECK (status IN ('pending', 'confirmed', 'cancelled', 'no_show', 'completed')),
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    CHECK (end_at > start_at)
);
CREATE INDEX idx_resource_bookings_resource_period ON resource_bookings(resource_id, start_at, end_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_resource_bookings_organizer ON resource_bookings(organizer_id, start_at DESC) WHERE deleted_at IS NULL;
COMMENT ON TABLE resource_bookings IS 'Réservations de ressources (différent de réservations restaurant)';

CREATE TABLE resource_booking_guests (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      uuid NOT NULL REFERENCES resource_bookings(id) ON DELETE CASCADE,
    guest_user_id   uuid REFERENCES users(id) ON DELETE SET NULL,
    guest_name      text,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_resource_booking_guests_booking ON resource_booking_guests(booking_id);
COMMENT ON TABLE resource_booking_guests IS 'Invités d''une réservation de ressource';

-- ─── modules/financial §11 ───────────────────────────────────────────

CREATE TABLE contracts (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   uuid NOT NULL REFERENCES restaurants(id) ON DELETE RESTRICT,
    contract_number text NOT NULL UNIQUE,
    commission_rate numeric(5, 2) NOT NULL CHECK (commission_rate >= 0 AND commission_rate <= 100),
    starts_at       date NOT NULL,
    ends_at         date,
    status          text NOT NULL DEFAULT 'active' CHECK (status IN ('draft', 'active', 'paused', 'terminated')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL,
    updated_by      uuid REFERENCES users(id) ON DELETE SET NULL,
    CHECK (ends_at IS NULL OR ends_at >= starts_at)
);
CREATE INDEX idx_contracts_restaurant ON contracts(restaurant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_contracts_status ON contracts(status) WHERE deleted_at IS NULL;
COMMENT ON TABLE contracts IS 'Contrats partenaires (commission_rate par restaurant)';

CREATE TABLE invoices (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   uuid NOT NULL REFERENCES restaurants(id) ON DELETE RESTRICT,
    invoice_number  text NOT NULL UNIQUE,
    period_start    date NOT NULL,
    period_end      date NOT NULL,
    subtotal        numeric(12, 2) NOT NULL DEFAULT 0,
    tva_amount      numeric(12, 2) NOT NULL DEFAULT 0,
    total_ttc       numeric(12, 2) NOT NULL DEFAULT 0,
    status          text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'sent', 'paid', 'overdue', 'cancelled')),
    issued_at       date,
    due_at          date,
    paid_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (period_end >= period_start)
);
CREATE INDEX idx_invoices_restaurant_period ON invoices(restaurant_id, period_start DESC);
CREATE INDEX idx_invoices_status ON invoices(status);
COMMENT ON TABLE invoices IS 'Factures mensuelles par restaurant (commission)';

CREATE TABLE invoice_lines (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      uuid NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    label           text NOT NULL,
    quantity        numeric(10, 2) NOT NULL DEFAULT 1 CHECK (quantity >= 0),
    unit_price      numeric(12, 2) NOT NULL DEFAULT 0,
    line_total      numeric(12, 2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    sort_order      integer NOT NULL DEFAULT 0
);
CREATE INDEX idx_invoice_lines_invoice ON invoice_lines(invoice_id, sort_order);
COMMENT ON TABLE invoice_lines IS 'Lignes de facture (détail par item)';

CREATE TABLE wallet_transactions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   uuid NOT NULL REFERENCES restaurants(id) ON DELETE RESTRICT,
    type            text NOT NULL CHECK (type IN ('credit', 'debit', 'commission', 'payout', 'adjustment')),
    amount          numeric(12, 2) NOT NULL,
    balance_after   numeric(12, 2),
    reason          text,
    reference_id    uuid,
    reference_type  text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_wallet_transactions_restaurant_date ON wallet_transactions(restaurant_id, created_at DESC);
CREATE INDEX idx_wallet_transactions_reference ON wallet_transactions(reference_type, reference_id);
COMMENT ON TABLE wallet_transactions IS 'Mouvements du wallet restaurateur (crédits, débits, commissions)';

-- ─── modules/payment §12 ─────────────────────────────────────────────
-- NOUVEAU module — n'existait pas dans le legacy

CREATE TABLE payment_methods (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            text NOT NULL CHECK (type IN ('card', 'bank_account', 'wallet', 'cash_on_site')),
    last4           text,
    provider        text,
    provider_token  text,
    is_default      boolean NOT NULL DEFAULT false,
    expires_at      date,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz
);
CREATE INDEX idx_payment_methods_user ON payment_methods(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_payment_methods_user_default ON payment_methods(user_id) WHERE deleted_at IS NULL AND is_default = true;
COMMENT ON TABLE payment_methods IS 'Moyens de paiement enregistrés par user (carte tokenisée, wallet, etc.)';

CREATE TABLE payments (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    payment_method_id   uuid REFERENCES payment_methods(id) ON DELETE SET NULL,
    amount              numeric(12, 2) NOT NULL CHECK (amount > 0),
    currency            text NOT NULL DEFAULT 'MAD',
    status              text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'succeeded', 'failed', 'cancelled', 'refunded')),
    provider            text,
    transaction_ref     text,
    reference_type      text,
    reference_id        uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    completed_at        timestamptz
);
CREATE INDEX idx_payments_user_date ON payments(user_id, created_at DESC);
CREATE INDEX idx_payments_status ON payments(status, created_at DESC);
CREATE INDEX idx_payments_provider_ref ON payments(provider, transaction_ref);
COMMENT ON TABLE payments IS 'Paiements (carte, wallet, ...) — réservations, redemptions, etc.';

CREATE TABLE refunds (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      uuid NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    amount          numeric(12, 2) NOT NULL CHECK (amount > 0),
    reason          text,
    status          text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'succeeded', 'failed')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    processed_at    timestamptz,
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_refunds_payment ON refunds(payment_id);
COMMENT ON TABLE refunds IS 'Remboursements partiels ou totaux liés à un payment';

CREATE TABLE payment_transactions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          uuid NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    provider_response   jsonb NOT NULL DEFAULT '{}'::jsonb,
    event_type          text NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_transactions_payment ON payment_transactions(payment_id, created_at DESC);
COMMENT ON TABLE payment_transactions IS 'Journal des événements provider (Stripe webhook, CMI callback, etc.)';
