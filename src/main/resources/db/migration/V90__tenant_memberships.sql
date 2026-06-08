-- ════════════════════════════════════════════════════════════════════════
-- V90 — tenant_memberships : appartenance ADDITIVE d'un client à un programme
-- ════════════════════════════════════════════════════════════════════════
-- Politique « 1 seul compte OneClick + memberships additives » (P0, additif).
--
-- Découple le tenant *home* (users.tenant_id) de l'accès aux PROGRAMMES (PCC, HOMU,
-- futurs) : un client garde un compte OneClick unique et peut être MEMBRE de N
-- programmes via cette table (octroyée par l'admin du tenant — P2). Source unique
-- de l'accès programme ; remplacera à terme le scoping dispersé par users.tenant_id.
--
-- P0 = strictement additif et iso-comportement : table + backfill MIROIR (chaque
-- client déjà rattaché à un programme reçoit une membership active reflétant l'état
-- actuel). Aucune lecture applicative ne s'appuie encore dessus (cf P1).

CREATE TABLE tenant_memberships (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users(id),
    tenant_id   uuid NOT NULL REFERENCES tenants(id),
    member_type varchar(32),                       -- resident / non_resident / NULL (repris de users.pcc_member_type)
    role_id     uuid REFERENCES roles(id),         -- rôle programme (ex. PCC_MEMBER) — utilisé en P1 (pliage authorities)
    status      varchar(32) NOT NULL DEFAULT 'active',
    invited_by  uuid REFERENCES users(id),         -- admin tenant ayant invité (P2)
    joined_at   timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    CONSTRAINT chk_tenant_memberships_status CHECK (status IN ('invited','active','revoked'))
);

COMMENT ON TABLE tenant_memberships IS
    'Appartenance additive client→programme (tenant). 1 compte OneClick peut être membre de N programmes. Source unique de l''accès programme.';

-- Une seule membership VIVANTE par (user, tenant) ; un revoke (deleted_at) autorise une ré-invitation ultérieure.
CREATE UNIQUE INDEX uq_tenant_memberships_user_tenant_alive
    ON tenant_memberships (user_id, tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenant_memberships_user   ON tenant_memberships (user_id)   WHERE deleted_at IS NULL;
CREATE INDEX idx_tenant_memberships_tenant ON tenant_memberships (tenant_id) WHERE deleted_at IS NULL;

-- ── Backfill MIROIR ──────────────────────────────────────────────────────
-- Chaque CLIENT rattaché à un PROGRAMME (tenant != oneclick, non NULL) reçoit une
-- membership active reflétant son tenant actuel + son type (pcc_member_type). Les
-- clients oneclick / sans tenant n'ont PAS de membership (socle seul). Sélection
-- par slug (aucun UUID en dur).
INSERT INTO tenant_memberships (id, user_id, tenant_id, member_type, status, joined_at, created_at, updated_at)
SELECT gen_random_uuid(), u.id, u.tenant_id, u.pcc_member_type, 'active', u.created_at, now(), now()
FROM users u
JOIN roles r ON r.id = u.role_id
WHERE r.code = 'CLIENT'
  AND u.deleted_at IS NULL
  AND u.tenant_id IS NOT NULL
  AND u.tenant_id NOT IN (SELECT id FROM tenants WHERE slug = 'oneclick')
ON CONFLICT DO NOTHING;
