-- ════════════════════════════════════════════════════════════════════
-- V78 — Invitations tenant-admin par email (E2 TenantWelcome, axe E)
-- ════════════════════════════════════════════════════════════════════
-- Un SUPERADMIN invite un futur administrateur de tenant par email. L'invité
-- reçoit un lien magique (token usage unique) → page /onboarding/welcome où il
-- crée son mot de passe → compte créé/activé + assignation tenant_admins + JWT.
--
-- Port du flux legacy Supabase (magic-link invite + user_metadata.password_set +
-- auth.updateUser) qui n'a pas d'équivalent natif Spring → on le matérialise via
-- une table d'invitations + token hashé (SHA-256) à usage unique.
--
-- Sécurité : on ne stocke JAMAIS le token en clair (seulement son SHA-256). Le
-- token clair ne vit que dans le lien email. Expiration 7 jours, single-use.
--
-- RBAC : pas de nouvelle ressource — création/liste/révocation via
-- VIEW/UPDATE:TENANTS (SUPERADMIN, déjà accordé V32). L'acceptation est PUBLIQUE
-- (l'invité n'a pas encore de compte) — gardée par le token, pas par une authority.
--
-- Additive pure (nouvelle table) → 0 impact existant.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS tenant_admin_invites (
    id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email            varchar(256)  NOT NULL,
    token_hash       varchar(128)  NOT NULL,
    tenant_role      varchar(16)   NOT NULL DEFAULT 'admin',
    status           varchar(16)   NOT NULL DEFAULT 'pending',
    invited_by       uuid          REFERENCES users(id) ON DELETE SET NULL,
    expires_at       timestamptz   NOT NULL,
    accepted_at      timestamptz,
    accepted_user_id uuid          REFERENCES users(id) ON DELETE SET NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT tenant_admin_invites_role_chk   CHECK (tenant_role IN ('owner', 'admin', 'viewer')),
    CONSTRAINT tenant_admin_invites_status_chk CHECK (status IN ('pending', 'accepted', 'revoked', 'expired')),
    CONSTRAINT tenant_admin_invites_token_uq   UNIQUE (token_hash)
);

-- Liste des invitations d'un tenant (portail admin).
CREATE INDEX IF NOT EXISTS idx_tenant_admin_invites_tenant ON tenant_admin_invites (tenant_id);
-- Filtre par statut (pending en attente).
CREATE INDEX IF NOT EXISTS idx_tenant_admin_invites_status ON tenant_admin_invites (status);

COMMENT ON TABLE tenant_admin_invites IS
    'E2 — invitations tenant-admin par email (magic-link, token SHA-256 single-use, expiry 7j).';
