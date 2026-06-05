-- ════════════════════════════════════════════════════════════════════
-- V70 — « Annonces tenant » (Lot 8) — comm B2B staff descendante (Adil → owners)
-- ════════════════════════════════════════════════════════════════════
-- Système d'annonces internes B2B pour communication staff descendante (un
-- tenant-admin — Adil pour PCC — publie une annonce que les owners/staff du
-- tenant lisent et acquittent). Architecture tenant-agnostic : activable par
-- n'importe quel tenant whitelabel (HOMU, Restopro, OneClick) via un feature flag
-- JSONB, sans toucher au code.
--
-- Port fidèle du legacy Supabase 20260430150000_tenant_announcements.sql vers un
-- module Spring CLOSED `modules/announcement`.
--
-- DIFFÉRENCES SENIOR vs legacy (toutes GÉNÉRIQUES, 0 hardcode palmeraie) :
--   • Pas de triggers SQL : toute la logique métier (max 1 épinglée par priorité,
--     bump body_version + reset reads à l'édition du body, soft-delete) vit dans
--     AnnouncementService (testable, transactionnel, traçable). On garde juste les
--     contraintes/index DDL ici.
--   • « tenant-admin » = user de rôle GROUP_ADMIN/SUPERADMIN rattaché au tenant
--     (table users.tenant_id + roles), PAS une table tenant_admins (inexistante en
--     Spring). « staff actif du tenant » = restaurant_staffs (role_code quelconque,
--     soft-delete deleted_at IS NULL) JOIN restaurants sur tenant_id. Read-views
--     natives côté repo (même pattern que modules/feedback findVisibleForOwner).
--   • Pas de bucket Storage ni de RLS : l'autorisation est portée par @PreAuthorize
--     (hasAuthority) + ABAC service (tenant-scope + admin-only write). image_url
--     reste une string (upload via mediaService existant côté front — V1 hors scope).
--
-- Workflow : created (publish_at <= now → visible staff) | scheduled (publish_at
-- futur → visible admin seul) | archived (archived_at posé) | deleted (soft).
-- Édition du body → body_version++ + DELETE des reads → ré-acquittement requis.
--
-- RBAC v2 senior strict : @PreAuthorize("hasAuthority('VERB:ANNOUNCEMENTS')")
-- partout (jamais isAuthenticated/hasRole). Grants (calque V66/V68/V69) :
--   • VIEW:ANNOUNCEMENTS   → STAFF, RESTAURATEUR, GROUP_ADMIN, SUPERADMIN
--       (PAS CLIENT — comm interne B2B staff only). Le staff lit les annonces
--       publiées de son tenant ; les tenant-admins voient tout (scheduled + archived).
--   • CREATE/UPDATE/DELETE:ANNOUNCEMENTS → RESTAURATEUR, GROUP_ADMIN, SUPERADMIN
--       (les « tenant-admins » qui publient). NB : STAFF n'a PAS d'écriture (lecture
--       seule). Le mark-read côté staff est gardé par VIEW:ANNOUNCEMENTS (self,
--       ABAC service) — pas par UPDATE, car ce n'est pas une mutation de l'annonce.
--
-- Anti-régression : tables dédiées + colonne tenants.features additive,
-- 0 impact OneClick/HOMU/Restopro (flag défaut '{}').
--
-- ⚠️ Après application : flusher le cache userDetails (redis-cli FLUSHDB) sinon les
-- sessions en cache n'ont pas les nouvelles autorités avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- ── 1. Feature flags par tenant (JSONB extensible) ──────────────────────────
-- Additif : 0 impact sur les tenants existants (défaut '{}'). Permet d'activer
-- des features par tenant sans toucher au code (ex: has_announcements, has_pkpass).
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS features jsonb NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN tenants.features IS
    'Feature flags par tenant (JSONB). Ex: {"has_announcements": true}. Activation par tenant sans toucher au code.';

-- Active la feature pour PCC (seul tenant V1 — démo Adil). Idempotent (|| merge).
UPDATE tenants
SET features = features || '{"has_announcements": true}'::jsonb
WHERE slug = 'palmeraie';

-- ── 2. Priorité = varchar + CHECK (PAS d'ENUM Postgres) ─────────────────────
-- Choix senior : la priorité est mappée varchar(16) côté entité (String), comme
-- reservations.status. Un ENUM Postgres natif (announcement_priority) imposerait un
-- CAST explicite à chaque INSERT/UPDATE Hibernate (« column is of type X but
-- expression is of type character varying ») — friction inutile. Le CHECK garantit
-- le même invariant (urgent|permanent) au niveau base, sans cette friction.

-- ── 3. Table tenant_announcements ───────────────────────────────────────────
-- PAS de triggers : la logique pinned/body_version/soft-delete est dans le SERVICE.
CREATE TABLE IF NOT EXISTS tenant_announcements (
    id           uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    author_id    uuid          NOT NULL REFERENCES users(id) ON DELETE SET NULL,
    title        varchar(100)  NOT NULL,
    body         varchar(1000) NOT NULL,
    image_url    text,
    priority     varchar(16)   NOT NULL DEFAULT 'permanent',
    is_pinned    boolean       NOT NULL DEFAULT true,
    publish_at   timestamptz   NOT NULL DEFAULT now(),  -- programmable (futur = scheduled)
    archived_at  timestamptz,
    deleted_at   timestamptz,                           -- soft delete (admin DELETE explicite)
    body_version int           NOT NULL DEFAULT 1,      -- bump à chaque édition body → reset reads
    push_sent_at timestamptz,                           -- track cron (éviter double push) — V1 hors scope
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT tenant_announcements_title_chk    CHECK (char_length(title) BETWEEN 1 AND 100),
    CONSTRAINT tenant_announcements_body_chk     CHECK (char_length(body)  BETWEEN 1 AND 1000),
    CONSTRAINT tenant_announcements_priority_chk CHECK (priority IN ('urgent', 'permanent'))
);

-- Index « annonce active épinglée » (le plus chaud, au mount du dashboard staff).
-- Partiel : on ne garde que les annonces vivantes (ni archivées, ni supprimées).
CREATE INDEX IF NOT EXISTS idx_announcements_tenant_pinned_active
    ON tenant_announcements (tenant_id, priority, is_pinned)
    WHERE archived_at IS NULL AND deleted_at IS NULL;

-- Index cron « annonces programmées à dépiler » (push différé) — V1 hors scope mais
-- l'index reste utile au filtre publish_at <= now() de la lecture staff.
CREATE INDEX IF NOT EXISTS idx_announcements_scheduled_pending
    ON tenant_announcements (publish_at)
    WHERE push_sent_at IS NULL AND deleted_at IS NULL;

-- Index feed chronologique tenant-scoped (liste admin + staff).
CREATE INDEX IF NOT EXISTS idx_announcements_tenant_created
    ON tenant_announcements (tenant_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- ── 4. Table announcement_reads (acquittement par body_version) ──────────────
-- PK composite (announcement_id, user_id) : 1 ligne par couple. Le mark-read est
-- un upsert idempotent ; body_version_read = la version acquittée (l'édition du
-- body côté service DELETE ces lignes → ré-acquittement requis).
CREATE TABLE IF NOT EXISTS announcement_reads (
    announcement_id   uuid        NOT NULL REFERENCES tenant_announcements(id) ON DELETE CASCADE,
    user_id           uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body_version_read int         NOT NULL DEFAULT 1,
    read_at           timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (announcement_id, user_id)
);

-- Lecture « mes acquittements » (état lu/non-lu d'un user sur une liste).
CREATE INDEX IF NOT EXISTS idx_announcement_reads_user
    ON announcement_reads (user_id);

-- ════════════════════════════════════════════════════════════════════
-- 5. RBAC — ressource/menu ANNOUNCEMENTS (feuille sous Support & Tickets)
-- ════════════════════════════════════════════════════════════════════
INSERT INTO menus (code, name, parent_id, sort_order)
SELECT 'ANNOUNCEMENTS', 'Annonces',
       (SELECT id FROM menus WHERE code = 'SUPPORT'), 66
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'ANNOUNCEMENTS');

-- ── 5.1 VIEW:ANNOUNCEMENTS → STAFF + RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ──
--    (comm interne B2B : staff only, PAS de CLIENT)
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('STAFF', 'RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'ANNOUNCEMENTS'
  AND a.code = 'VIEW'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 5.2 CREATE:ANNOUNCEMENTS → RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ───────
--    (les « tenant-admins » qui publient ; ABAC service restreint au tenant + admin-only)
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'ANNOUNCEMENTS'
  AND a.code = 'CREATE'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 5.3 UPDATE:ANNOUNCEMENTS → RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ───────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'ANNOUNCEMENTS'
  AND a.code = 'UPDATE'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 5.4 DELETE:ANNOUNCEMENTS → RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ───────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'ANNOUNCEMENTS'
  AND a.code = 'DELETE'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ════════════════════════════════════════════════════════════════════
-- 6. Seed démo (idempotent) : 1 annonce permanente épinglée tenant palmeraie,
--    publiée par un owner (RESTAURATEUR du tenant). Démontre une bannière non-vide.
--    Guard NOT EXISTS sur (tenant_id, title) → ré-exécution sûre.
-- ════════════════════════════════════════════════════════════════════
INSERT INTO tenant_announcements (tenant_id, author_id, title, body, priority, is_pinned, publish_at)
SELECT t.id,
       (SELECT u.id
        FROM users u JOIN roles r ON r.id = u.role_id
        WHERE u.tenant_id = t.id AND r.code = 'RESTAURATEUR' AND u.deleted_at IS NULL
        ORDER BY u.created_at LIMIT 1),
       'Bienvenue sur les annonces PCC',
       'Adil pourra désormais publier ici les informations importantes pour toute l''équipe (horaires, événements, consignes). Les annonces épinglées restent en tête.',
       'permanent', true, now()
FROM tenants t
WHERE t.slug = 'palmeraie'
  AND EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id = u.role_id
              WHERE u.tenant_id = t.id AND r.code = 'RESTAURATEUR' AND u.deleted_at IS NULL)
  AND NOT EXISTS (SELECT 1 FROM tenant_announcements a
                  WHERE a.tenant_id = t.id AND a.title = 'Bienvenue sur les annonces PCC');
