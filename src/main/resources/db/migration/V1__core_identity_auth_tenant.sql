-- ════════════════════════════════════════════════════════════════════
-- V1 — Foundation : core/identity + core/auth + core/tenant
-- ════════════════════════════════════════════════════════════════════
-- Architecture cible : oneclick_architecture_enterprise_optimized.md §2, §3
-- Greenfield : 12 tables foundation pour RBAC + tenant
-- ════════════════════════════════════════════════════════════════════

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── core/tenant ─────────────────────────────────────────────────────
-- Multi-tenant racine — chaque tenant (OneClick, HOMU, Palmeraie, Restopro)
-- a son propre branding, ses propres features et sa config légale (ICE, RIB).
CREATE TABLE tenants (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            text NOT NULL,
    slug            text NOT NULL UNIQUE,
    status          text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'paused', 'archived')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid
);
CREATE INDEX idx_tenants_slug ON tenants(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_status ON tenants(status) WHERE deleted_at IS NULL;
COMMENT ON TABLE tenants IS 'Racine multi-tenant — 1 ligne par marque whitelabel (OneClick, HOMU, PCC, Restopro)';

CREATE TABLE tenant_brandings (
    tenant_id       uuid PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    logo_url        text,
    primary_color   text,
    accent_color    text,
    custom_domain   text UNIQUE,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE tenant_brandings IS 'Branding visuel par tenant — logo, couleurs, domaine custom';

CREATE TABLE tenant_features (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    feature_code    text NOT NULL,
    enabled         boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, feature_code)
);
CREATE INDEX idx_tenant_features_tenant ON tenant_features(tenant_id) WHERE enabled = true;
COMMENT ON TABLE tenant_features IS 'Feature flags par tenant (loyalty, reservations, community, etc.)';

CREATE TABLE company_settings (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    raison_sociale  text NOT NULL,
    ice             text,
    rib             text,
    tva_rate        numeric(5, 2) NOT NULL DEFAULT 20.00,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE company_settings IS 'Configuration légale/facturation par tenant (ICE Maroc, RIB, TVA)';

-- ─── core/identity ───────────────────────────────────────────────────
-- RBAC simplifié : 1 user = 1 rôle (vs many-to-many actuel)
-- Modèle Spring Security classique : User → Role → Permission (Menu + Action)

CREATE TABLE roles (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE roles IS 'Rôles applicatifs : ADMIN, RESTAURATEUR, STAFF, CLIENT, TENANT_ADMIN, etc.';

CREATE TABLE menus (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    icon            text,
    path            text,
    parent_id       uuid REFERENCES menus(id) ON DELETE CASCADE,
    sort_order      integer NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_menus_parent ON menus(parent_id);
CREATE INDEX idx_menus_sort ON menus(parent_id, sort_order);
COMMENT ON TABLE menus IS 'Arbre de menus applicatifs (sidebar) avec ordre + parent';

CREATE TABLE actions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    module          text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_actions_module ON actions(module);
COMMENT ON TABLE actions IS 'Actions métier (CREATE_RESERVATION, CANCEL_RESERVATION, ...) regroupées par module';

CREATE TABLE permissions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id         uuid NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    menu_id         uuid REFERENCES menus(id) ON DELETE CASCADE,
    action_id       uuid REFERENCES actions(id) ON DELETE CASCADE,
    created_at      timestamptz NOT NULL DEFAULT now(),
    -- Soit menu_id soit action_id (pas les deux NULL — permission orpheline)
    CHECK (menu_id IS NOT NULL OR action_id IS NOT NULL),
    UNIQUE (role_id, menu_id, action_id)
);
CREATE INDEX idx_permissions_role ON permissions(role_id);
COMMENT ON TABLE permissions IS 'Junction role × (menu | action) — la liste des choses qu un rôle peut faire/voir';

CREATE TABLE users (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   uuid REFERENCES tenants(id) ON DELETE RESTRICT,
    role_id                     uuid NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    email                       text NOT NULL UNIQUE,
    phone                       text UNIQUE,
    password_hash               text NOT NULL,
    first_name                  text NOT NULL,
    last_name                   text NOT NULL,
    avatar_url                  text,
    language                    text NOT NULL DEFAULT 'fr' CHECK (language IN ('fr', 'en', 'ar')),
    status                      text NOT NULL DEFAULT 'active',
    -- Flags Spring Security natifs (§2.1)
    account_non_expired         boolean NOT NULL DEFAULT true,
    account_non_locked          boolean NOT NULL DEFAULT true,
    credentials_non_expired     boolean NOT NULL DEFAULT true,
    enabled                     boolean NOT NULL DEFAULT true,
    last_login_at               timestamptz,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    deleted_at                  timestamptz,
    created_by                  uuid,
    updated_by                  uuid
);
CREATE INDEX idx_users_tenant ON users(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_role ON users(role_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_email ON users(LOWER(email)) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_phone ON users(phone) WHERE deleted_at IS NULL AND phone IS NOT NULL;
COMMENT ON TABLE users IS 'Identité applicative — 1 user = 1 role (RBAC simplifié vs many-to-many legacy)';

-- ─── core/auth ───────────────────────────────────────────────────────
-- Auth flow : refresh tokens + login history + OTP

CREATE TABLE refresh_tokens (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           text NOT NULL UNIQUE,
    expires_at      timestamptz NOT NULL,
    revoked_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at) WHERE revoked_at IS NULL;
COMMENT ON TABLE refresh_tokens IS 'Refresh tokens JWT (rotation, révocation possible)';

CREATE TABLE login_histories (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid REFERENCES users(id) ON DELETE SET NULL,
    ip_address      text,
    device          text,
    success         boolean NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_histories_user ON login_histories(user_id, created_at DESC);
CREATE INDEX idx_login_histories_failed ON login_histories(user_id, created_at DESC) WHERE success = false;
COMMENT ON TABLE login_histories IS 'Historique des tentatives de connexion (sécurité, anti brute-force)';

CREATE TABLE otp_requests (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose         text NOT NULL CHECK (purpose IN ('signup', 'reset_password', 'verify_phone', 'verify_email', '2fa', 'redemption')),
    code            text NOT NULL,
    expires_at      timestamptz NOT NULL,
    verified_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_requests_user_purpose ON otp_requests(user_id, purpose) WHERE verified_at IS NULL;
CREATE INDEX idx_otp_requests_expires ON otp_requests(expires_at) WHERE verified_at IS NULL;
COMMENT ON TABLE otp_requests IS 'Codes OTP (signup, reset password, 2FA, redemption Snap2Earn)';
