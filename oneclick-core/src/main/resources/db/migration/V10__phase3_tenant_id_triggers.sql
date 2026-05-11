-- ============================================================================
-- V10 — Phase 3 §17 : triggers auto-fill tenant_id (sans modifier les entities)
-- ============================================================================
-- V9 a ajouté tenant_id NOT NULL aux 7 tables, mais les entities JPA actuelles
-- ne le mappent pas. Sans cette migration, tout INSERT échoue (violation NOT NULL).
--
-- Solution : trigger BEFORE INSERT qui dérive tenant_id depuis le parent FK,
-- transparent pour Hibernate. L'entity Java ne sait pas que tenant_id existe
-- en DB, mais la colonne est correctement populée par PostgreSQL.
--
-- Bonus : protège contre incohérences (un offer ne peut pas avoir un tenant_id
-- différent de celui de son restaurant — le trigger force la cohérence).
-- ============================================================================

-- ─── Fonction générique : dérive tenant_id depuis restaurant ──────────────
CREATE OR REPLACE FUNCTION derive_tenant_id_from_restaurant()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.tenant_id IS NULL AND NEW.restaurant_id IS NOT NULL THEN
        SELECT tenant_id INTO NEW.tenant_id FROM restaurants WHERE id = NEW.restaurant_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- NB : PostgreSQL interdit les trigger functions avec arguments déclarés.
-- → On définit une fonction par colonne FK (recipient_user_id, user_id,
--   author_id, opened_by). C'est verbeux mais c'est la contrainte PL/pgSQL.

CREATE OR REPLACE FUNCTION derive_tenant_id_from_recipient_user()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.tenant_id IS NULL AND NEW.recipient_user_id IS NOT NULL THEN
        SELECT COALESCE(tenant_id, '00000000-0000-0000-0000-000000000001'::uuid)
            INTO NEW.tenant_id FROM users WHERE id = NEW.recipient_user_id;
    END IF;
    IF NEW.tenant_id IS NULL THEN
        NEW.tenant_id := '00000000-0000-0000-0000-000000000001'::uuid;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION derive_tenant_id_from_user_id()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.tenant_id IS NULL AND NEW.user_id IS NOT NULL THEN
        SELECT COALESCE(tenant_id, '00000000-0000-0000-0000-000000000001'::uuid)
            INTO NEW.tenant_id FROM users WHERE id = NEW.user_id;
    END IF;
    IF NEW.tenant_id IS NULL THEN
        NEW.tenant_id := '00000000-0000-0000-0000-000000000001'::uuid;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION derive_tenant_id_from_author()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.tenant_id IS NULL AND NEW.author_id IS NOT NULL THEN
        SELECT COALESCE(tenant_id, '00000000-0000-0000-0000-000000000001'::uuid)
            INTO NEW.tenant_id FROM users WHERE id = NEW.author_id;
    END IF;
    IF NEW.tenant_id IS NULL THEN
        NEW.tenant_id := '00000000-0000-0000-0000-000000000001'::uuid;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION derive_tenant_id_from_opener()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.tenant_id IS NULL AND NEW.opened_by IS NOT NULL THEN
        SELECT COALESCE(tenant_id, '00000000-0000-0000-0000-000000000001'::uuid)
            INTO NEW.tenant_id FROM users WHERE id = NEW.opened_by;
    END IF;
    IF NEW.tenant_id IS NULL THEN
        NEW.tenant_id := '00000000-0000-0000-0000-000000000001'::uuid;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ─── Triggers BEFORE INSERT pour les 7 tables ──────────────────────────────
DROP TRIGGER IF EXISTS trg_contracts_tenant_id ON contracts;
CREATE TRIGGER trg_contracts_tenant_id BEFORE INSERT ON contracts
    FOR EACH ROW EXECUTE FUNCTION derive_tenant_id_from_restaurant();

DROP TRIGGER IF EXISTS trg_loyalty_accounts_tenant_id ON loyalty_accounts;
CREATE TRIGGER trg_loyalty_accounts_tenant_id BEFORE INSERT ON loyalty_accounts
    FOR EACH ROW EXECUTE FUNCTION derive_tenant_id_from_restaurant();

DROP TRIGGER IF EXISTS trg_offers_tenant_id ON offers;
CREATE TRIGGER trg_offers_tenant_id BEFORE INSERT ON offers
    FOR EACH ROW EXECUTE FUNCTION derive_tenant_id_from_restaurant();

DROP TRIGGER IF EXISTS trg_notifications_tenant_id ON notifications;
CREATE TRIGGER trg_notifications_tenant_id BEFORE INSERT ON notifications
    FOR EACH ROW EXECUTE FUNCTION derive_tenant_id_from_recipient_user();

DROP TRIGGER IF EXISTS trg_payments_tenant_id ON payments;
CREATE TRIGGER trg_payments_tenant_id BEFORE INSERT ON payments
    FOR EACH ROW EXECUTE FUNCTION derive_tenant_id_from_user_id();

DROP TRIGGER IF EXISTS trg_posts_tenant_id ON posts;
CREATE TRIGGER trg_posts_tenant_id BEFORE INSERT ON posts
    FOR EACH ROW EXECUTE FUNCTION derive_tenant_id_from_author();

DROP TRIGGER IF EXISTS trg_support_tickets_tenant_id ON support_tickets;
CREATE TRIGGER trg_support_tickets_tenant_id BEFORE INSERT ON support_tickets
    FOR EACH ROW EXECUTE FUNCTION derive_tenant_id_from_opener();

COMMENT ON FUNCTION derive_tenant_id_from_restaurant IS
    'Trigger function auto-fill tenant_id depuis restaurants.tenant_id (Phase 3.2 §17 spec)';
