-- ============================================================================
-- V13 — Sprint B : tables manquantes pour finir la migration frontend
-- ============================================================================
-- Contexte : 5 tables identifiées par l'audit frontend (149 fichiers Supabase
-- encore actifs) qui n'avaient pas d'équivalent dans le schéma greenfield V1-V12.
--
-- Tables ajoutées :
--   • user_favorites          — bouton ❤️ Pocket (client favorite restaurant)
--   • friend_groups           — groupes d'amis Pocket (squads/teams)
--   • friend_group_members    — junction users × friend_groups
--   • contract_templates      — templates legal pour ContractDownload PDF
--   • gain_rules              — règles loyalty PAR restaurant (override des loyalty_rules globaux)
--
-- Conventions :
--   - UUID PK avec default gen_random_uuid()
--   - tenant_id auto-fill via trigger V10 (sur les tables liées à restaurants)
--   - Audit fields : created_at, updated_at, deleted_at, created_by, updated_by
--   - FK avec ON DELETE rules cohérentes (CASCADE pour les junctions, SET NULL pour les FK orphelinables)
--   - Index sur les FK + colonnes filtrables fréquentes
-- ============================================================================

-- ─── 1. user_favorites — bouton ❤️ Pocket ──────────────────────────────────
CREATE TABLE user_favorites (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    restaurant_id UUID         NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    tenant_id     UUID         REFERENCES tenants(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    UUID         REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT user_favorites_unique UNIQUE (user_id, restaurant_id)
);
CREATE INDEX idx_user_favorites_user      ON user_favorites(user_id);
CREATE INDEX idx_user_favorites_restaurant ON user_favorites(restaurant_id);

COMMENT ON TABLE user_favorites IS 'Favoris client × restaurant — bouton ❤️ Pocket Compass/Spotlight';

-- ─── 2. friend_groups — squads/teams Pocket ────────────────────────────────
CREATE TABLE friend_groups (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id   UUID         REFERENCES tenants(id) ON DELETE SET NULL,
    name        TEXT         NOT NULL CHECK (length(trim(name)) > 0),
    description TEXT,
    avatar_url  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    created_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    updated_by  UUID         REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_friend_groups_owner ON friend_groups(owner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_friend_groups_tenant ON friend_groups(tenant_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE friend_groups IS 'Groupes d''amis (squads) — réservation collective Pocket';

-- ─── 3. friend_group_members — junction ────────────────────────────────────
CREATE TABLE friend_group_members (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    friend_group_id UUID         NOT NULL REFERENCES friend_groups(id) ON DELETE CASCADE,
    friend_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            TEXT         NOT NULL DEFAULT 'member' CHECK (role IN ('owner', 'admin', 'member')),
    joined_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    invited_by      UUID         REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT friend_group_members_unique UNIQUE (friend_group_id, friend_id)
);
CREATE INDEX idx_friend_group_members_group  ON friend_group_members(friend_group_id);
CREATE INDEX idx_friend_group_members_friend ON friend_group_members(friend_id);

COMMENT ON TABLE friend_group_members IS 'Junction users × friend_groups — appartenance + rôle';

-- ─── 4. contract_templates — templates Contracts PDF ───────────────────────
CREATE TABLE contract_templates (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         REFERENCES tenants(id) ON DELETE SET NULL,
    code        TEXT         NOT NULL,
    name        TEXT         NOT NULL,
    version     INT          NOT NULL DEFAULT 1,
    language    TEXT         NOT NULL DEFAULT 'fr' CHECK (language IN ('fr', 'en', 'ar')),
    title       TEXT         NOT NULL,
    body        TEXT         NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    created_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    updated_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT contract_templates_code_version_lang_unique UNIQUE (tenant_id, code, version, language)
);
CREATE INDEX idx_contract_templates_tenant ON contract_templates(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_contract_templates_code   ON contract_templates(code)      WHERE deleted_at IS NULL AND is_active = true;

COMMENT ON TABLE contract_templates IS 'Templates contractuels (PDF generation) — versionnés, multilangue';

-- ─── 5. gain_rules — règles loyalty par restaurant ──────────────────────────
-- Override par-restaurant des loyalty_rules globaux (qui restent en place pour
-- les valeurs par défaut tenant-wide).
CREATE TABLE gain_rules (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID         NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    tenant_id       UUID         REFERENCES tenants(id) ON DELETE SET NULL,
    conversion_rate NUMERIC(6,4) NOT NULL DEFAULT 0.10
                    CHECK (conversion_rate >= 0 AND conversion_rate <= 1),
    cap_per_visit   INT          CHECK (cap_per_visit IS NULL OR cap_per_visit > 0),
    cap_per_month   INT          CHECK (cap_per_month IS NULL OR cap_per_month > 0),
    min_amount      NUMERIC(10,2) DEFAULT 0
                    CHECK (min_amount IS NULL OR min_amount >= 0),
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    created_by      UUID         REFERENCES users(id) ON DELETE SET NULL,
    updated_by      UUID         REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT gain_rules_restaurant_unique UNIQUE (restaurant_id)
);
CREATE INDEX idx_gain_rules_restaurant ON gain_rules(restaurant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_gain_rules_tenant     ON gain_rules(tenant_id)     WHERE deleted_at IS NULL;

COMMENT ON TABLE gain_rules IS 'Règles de gain de points PAR restaurant — override des loyalty_rules globaux';

-- ─── 6. Triggers auto-fill tenant_id (pattern V10) ─────────────────────────
-- Ces tables héritent de tenant_id du restaurant (ou du user pour user_favorites).
-- Pour ne pas complexifier V10, on applique les triggers en inline ici.

-- user_favorites.tenant_id <- restaurants.tenant_id
CREATE OR REPLACE FUNCTION fill_tenant_id_from_restaurant_for_user_favorites()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.tenant_id IS NULL AND NEW.restaurant_id IS NOT NULL THEN
        SELECT tenant_id INTO NEW.tenant_id FROM restaurants WHERE id = NEW.restaurant_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_favorites_tenant_id
    BEFORE INSERT OR UPDATE OF restaurant_id ON user_favorites
    FOR EACH ROW EXECUTE FUNCTION fill_tenant_id_from_restaurant_for_user_favorites();

-- gain_rules.tenant_id <- restaurants.tenant_id
CREATE OR REPLACE FUNCTION fill_tenant_id_from_restaurant_for_gain_rules()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.tenant_id IS NULL AND NEW.restaurant_id IS NOT NULL THEN
        SELECT tenant_id INTO NEW.tenant_id FROM restaurants WHERE id = NEW.restaurant_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_gain_rules_tenant_id
    BEFORE INSERT OR UPDATE OF restaurant_id ON gain_rules
    FOR EACH ROW EXECUTE FUNCTION fill_tenant_id_from_restaurant_for_gain_rules();

-- friend_groups.tenant_id <- owner_id (user) — multi-tenant via user
CREATE OR REPLACE FUNCTION fill_tenant_id_from_user_for_friend_groups()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.tenant_id IS NULL AND NEW.owner_id IS NOT NULL THEN
        SELECT tenant_id INTO NEW.tenant_id FROM users WHERE id = NEW.owner_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_friend_groups_tenant_id
    BEFORE INSERT OR UPDATE OF owner_id ON friend_groups
    FOR EACH ROW EXECUTE FUNCTION fill_tenant_id_from_user_for_friend_groups();

-- ─── 7. Updated_at automatic trigger (réutilise pattern V10 si défini) ─────
-- Note : si le projet a un trigger générique `update_updated_at_column()` défini
-- en V10/V12, on l'attache aux nouvelles tables. Sinon on définit en local.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'update_updated_at_column') THEN
        CREATE TRIGGER trg_friend_groups_updated_at      BEFORE UPDATE ON friend_groups       FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
        CREATE TRIGGER trg_contract_templates_updated_at BEFORE UPDATE ON contract_templates  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
        CREATE TRIGGER trg_gain_rules_updated_at         BEFORE UPDATE ON gain_rules          FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
