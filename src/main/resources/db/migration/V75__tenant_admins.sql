-- ════════════════════════════════════════════════════════════════════
-- V75 — Administrateurs de tenant (portail tenant-admin C2, SUPERADMIN-only)
-- ════════════════════════════════════════════════════════════════════
-- Lie des utilisateurs à un tenant en tant qu'administrateurs (owner/admin/viewer).
-- Port du legacy public.tenant_admins (whitelabel_foundation). Adaptation Spring :
-- clé surrogate `id` + contrainte UNIQUE(tenant_id, user_id) (pattern tenant_features)
-- au lieu de la PK composite legacy — fonctionnellement équivalent, plus simple pour JPA.
--
-- RBAC : pas de nouvelle ressource — la gestion passe par VIEW/UPDATE:TENANTS (SUPERADMIN,
-- déjà accordé V32). Aucune migration RBAC.
--
-- Additive pure (nouvelle table) → 0 impact existant.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS tenant_admins (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     uuid          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        varchar(16)   NOT NULL DEFAULT 'admin',
    invited_by  uuid          REFERENCES users(id) ON DELETE SET NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT tenant_admins_role_chk   CHECK (role IN ('owner', 'admin', 'viewer')),
    CONSTRAINT tenant_admins_unique     UNIQUE (tenant_id, user_id)
);

-- Liste des admins d'un tenant.
CREATE INDEX IF NOT EXISTS idx_tenant_admins_tenant ON tenant_admins (tenant_id);
