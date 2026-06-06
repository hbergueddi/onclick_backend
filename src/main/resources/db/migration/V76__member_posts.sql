-- ════════════════════════════════════════════════════════════════════
-- V76 — « Circle » : posts membres + modération (C4.8c, tranche modération)
-- ════════════════════════════════════════════════════════════════════
-- Réseau social privé membres (legacy member_circle phase 1, 08/05/2026). Cette
-- migration ne crée QUE le socle nécessaire à la MODÉRATION tenant-admin (lister
-- pending/approved/rejected + approuver/rejeter/supprimer). Le flux membre complet
-- (création, feed RPC, likes, commentaires, mentions) est un lot futur séparé.
--
-- Architecture tenant-agnostic (pas de hardcode palmeraie) : un post appartient à un
-- tenant + un auteur (membre). Statut pending → approved | rejected. Soft-delete.
--
-- RBAC : la modération vit dans le portail tenant-admin SUPERADMIN-only → gardée par
-- @PreAuthorize hasAuthority('VIEW/UPDATE/DELETE:TENANTS') (SUPERADMIN les possède
-- déjà via V32) — pas de nouvelle ressource RBAC ni de grants. Cohérent avec C4.1-C4.8b.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS member_posts (
    id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    author_id        uuid          NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    content          varchar(500)  NOT NULL,
    photo_url        varchar(512),
    activity_tag     varchar(32),
    status           varchar(16)   NOT NULL DEFAULT 'pending',
    rejection_reason varchar(512),
    reviewed_at      timestamptz,
    reviewed_by      uuid          REFERENCES users(id) ON DELETE SET NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    deleted_at       timestamptz,                       -- soft delete (modération)
    CONSTRAINT member_posts_content_chk CHECK (char_length(content) BETWEEN 1 AND 500),
    CONSTRAINT member_posts_status_chk  CHECK (status IN ('pending', 'approved', 'rejected'))
);

-- Index modération : feed admin par tenant + statut, du plus récent au plus ancien.
CREATE INDEX IF NOT EXISTS idx_member_posts_tenant_status
    ON member_posts (tenant_id, status, created_at DESC)
    WHERE deleted_at IS NULL;
