-- ════════════════════════════════════════════════════════════════════
-- V18 — Sprint G.2.3 : gain_rule_requests (workflow approbation admin)
-- ════════════════════════════════════════════════════════════════════
-- Permet à un restaurateur de demander la création d'une règle de gain
-- (`gain_rules` existante en V13). L'admin approuve/refuse via
-- /api/loyalty/gain-rule-requests/{id}/approve|reject.
--
-- Si approuvée → la règle est créée (désactivée par défaut, le restaurateur
-- l'active manuellement après revue).
-- Si refusée → rejection_reason expliqué au restaurateur.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE gain_rule_requests (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id    UUID         NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    tenant_id        UUID         REFERENCES tenants(id) ON DELETE SET NULL,
    -- Données proposées pour la future gain_rule
    name             TEXT         NOT NULL CHECK (length(trim(name)) > 0),
    description      TEXT,
    type             TEXT         NOT NULL DEFAULT 'standard'
                     CHECK (type IN ('standard', 'premium', 'event', 'loyalty')),
    conversion_rate  NUMERIC(6,4) NOT NULL DEFAULT 0.10
                     CHECK (conversion_rate >= 0 AND conversion_rate <= 1),
    cap_per_visit    INT          CHECK (cap_per_visit IS NULL OR cap_per_visit > 0),
    cap_per_month    INT          CHECK (cap_per_month IS NULL OR cap_per_month > 0),
    min_amount       NUMERIC(10,2) DEFAULT 0
                     CHECK (min_amount IS NULL OR min_amount >= 0),
    -- Workflow approbation
    status           TEXT         NOT NULL DEFAULT 'pending'
                     CHECK (status IN ('pending', 'approved', 'rejected')),
    rejection_reason TEXT,
    reviewed_by      UUID         REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at      TIMESTAMPTZ,
    -- Si approuvée, référence à la gain_rule créée (audit trail)
    created_rule_id  UUID         REFERENCES gain_rules(id) ON DELETE SET NULL,
    -- Audit standard
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at       TIMESTAMPTZ,
    created_by       UUID         REFERENCES users(id) ON DELETE SET NULL,
    updated_by       UUID         REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_gain_rule_requests_restaurant
    ON gain_rule_requests(restaurant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_gain_rule_requests_status
    ON gain_rule_requests(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_gain_rule_requests_pending
    ON gain_rule_requests(created_at DESC)
    WHERE deleted_at IS NULL AND status = 'pending';

-- Trigger auto-fill tenant_id depuis restaurants (pattern V10)
CREATE OR REPLACE FUNCTION fill_tenant_id_from_restaurant_for_gain_rule_requests()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.tenant_id IS NULL AND NEW.restaurant_id IS NOT NULL THEN
        SELECT tenant_id INTO NEW.tenant_id FROM restaurants WHERE id = NEW.restaurant_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_gain_rule_requests_tenant_id
    BEFORE INSERT OR UPDATE OF restaurant_id ON gain_rule_requests
    FOR EACH ROW EXECUTE FUNCTION fill_tenant_id_from_restaurant_for_gain_rule_requests();

-- Trigger updated_at (réutilise pattern V10)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'update_updated_at_column') THEN
        CREATE TRIGGER trg_gain_rule_requests_updated_at
            BEFORE UPDATE ON gain_rule_requests
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

COMMENT ON TABLE gain_rule_requests IS
    'Workflow approbation des demandes de règle de gain (restaurateur → admin)';
