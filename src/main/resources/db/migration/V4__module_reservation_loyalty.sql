-- ════════════════════════════════════════════════════════════════════
-- V4 — modules/reservation (§5) + modules/loyalty (§6)
-- ════════════════════════════════════════════════════════════════════
-- 9 tables : 4 reservation + 5 loyalty
-- ════════════════════════════════════════════════════════════════════

-- ─── modules/reservation §5 ──────────────────────────────────────────
-- Note : reservation_at timestamptz UNIFIÉ (vs date + heure séparés en legacy)

CREATE TABLE reservations (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    client_id       uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    restaurant_id   uuid NOT NULL REFERENCES restaurants(id) ON DELETE RESTRICT,
    table_id        uuid REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    service_id      uuid REFERENCES restaurant_services(id) ON DELETE SET NULL,
    reservation_at  timestamptz NOT NULL,
    guest_count     integer NOT NULL CHECK (guest_count > 0),
    status          text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'confirmed', 'refused', 'counter_proposed', 'cancelled', 'honored', 'no_show')),
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL,
    updated_by      uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_reservations_client_date ON reservations(client_id, reservation_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_reservations_restaurant_date ON reservations(restaurant_id, reservation_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_reservations_status_date ON reservations(status, reservation_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_reservations_tenant ON reservations(tenant_id) WHERE deleted_at IS NULL;
COMMENT ON TABLE reservations IS 'Réservations — reservation_at unifié (vs date+heure legacy), workflow 7 statuts';

CREATE TABLE reservation_guests (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id      uuid NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    guest_user_id       uuid REFERENCES users(id) ON DELETE SET NULL,
    guest_name          text,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_reservation_guests_reservation ON reservation_guests(reservation_id);
COMMENT ON TABLE reservation_guests IS 'Invités d''une réservation (user OneClick ou nom libre)';

CREATE TABLE reservation_status_histories (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id  uuid NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    old_status      text,
    new_status      text NOT NULL,
    changed_by      uuid REFERENCES users(id) ON DELETE SET NULL,
    reason          text,
    changed_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_reservation_status_histories_reservation ON reservation_status_histories(reservation_id, changed_at DESC);
COMMENT ON TABLE reservation_status_histories IS 'Audit workflow des changements de statut (qui, quand, pourquoi)';

CREATE TABLE booking_rules (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   uuid NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    max_guest       integer NOT NULL DEFAULT 12,
    slot_duration   integer NOT NULL DEFAULT 90,  -- minutes
    cancellation_window_hours integer NOT NULL DEFAULT 2,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_booking_rules_restaurant ON booking_rules(restaurant_id);
COMMENT ON TABLE booking_rules IS 'Règles de réservation par restaurant (couverts max, durée slot, fenêtre annulation)';

-- ─── modules/loyalty §6 ──────────────────────────────────────────────
-- Refonte propre : LoyaltyAccount (balance) + LoyaltyTransaction (mouvements typés)
-- vs loyalty_points legacy (1 row par mouvement, calcul balance impossible)

CREATE TABLE tiers (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name            text NOT NULL,
    min_points      integer NOT NULL DEFAULT 0 CHECK (min_points >= 0),
    bonus_percent   numeric(5, 2) NOT NULL DEFAULT 0.00 CHECK (bonus_percent >= 0),
    sort_order      integer NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);
CREATE INDEX idx_tiers_tenant_order ON tiers(tenant_id, sort_order);
COMMENT ON TABLE tiers IS 'Niveaux de fidélité (Ruby, Sapphire, Emeraude...) avec bonus_percent et seuils min_points';

CREATE TABLE loyalty_accounts (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    restaurant_id   uuid NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    tier_id         uuid REFERENCES tiers(id) ON DELETE SET NULL,
    balance         integer NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (client_id, restaurant_id)
);
CREATE INDEX idx_loyalty_accounts_client ON loyalty_accounts(client_id);
CREATE INDEX idx_loyalty_accounts_restaurant ON loyalty_accounts(restaurant_id);
COMMENT ON TABLE loyalty_accounts IS 'Solde de points par couple (client, restaurant). 1 ligne = 1 compte fidélité';

CREATE TABLE loyalty_transactions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      uuid NOT NULL REFERENCES loyalty_accounts(id) ON DELETE CASCADE,
    type            text NOT NULL CHECK (type IN ('earn', 'spend', 'expire', 'gift', 'adjust')),
    points          integer NOT NULL,
    amount          numeric(12, 2),
    reason          text,
    expires_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_loyalty_transactions_account_date ON loyalty_transactions(account_id, created_at DESC);
CREATE INDEX idx_loyalty_transactions_type ON loyalty_transactions(type);
CREATE INDEX idx_loyalty_transactions_expires ON loyalty_transactions(expires_at) WHERE type = 'earn' AND expires_at IS NOT NULL;
COMMENT ON TABLE loyalty_transactions IS 'Mouvements de points typés (earn/spend/expire/gift/adjust). SUM(points) = balance';

CREATE TABLE loyalty_rules (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id       uuid NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    conversion_rate     numeric(5, 4) NOT NULL DEFAULT 0.05 CHECK (conversion_rate >= 0),  -- 5% par défaut
    max_points          integer NOT NULL DEFAULT 1000 CHECK (max_points > 0),
    min_ticket_amount   numeric(12, 2) NOT NULL DEFAULT 100.00 CHECK (min_ticket_amount >= 0),
    point_value         numeric(8, 4) NOT NULL DEFAULT 1.0000,  -- 1 pt = 1 MAD
    expires_after_days  integer NOT NULL DEFAULT 365 CHECK (expires_after_days > 0),
    enabled             boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_loyalty_rules_restaurant ON loyalty_rules(restaurant_id) WHERE enabled = true;
COMMENT ON TABLE loyalty_rules IS 'Règles de calcul des points par restaurant (conversion, plafonds, expiration)';

CREATE TABLE redemptions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          uuid NOT NULL REFERENCES loyalty_accounts(id) ON DELETE CASCADE,
    points_used         integer NOT NULL CHECK (points_used > 0),
    discount_amount     numeric(12, 2) NOT NULL CHECK (discount_amount > 0),
    otp_validated       boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_redemptions_account_date ON redemptions(account_id, created_at DESC);
COMMENT ON TABLE redemptions IS 'Utilisations de points (audit dédié) avec OTP validation pour gros montants';
