-- ════════════════════════════════════════════════════════════════════
-- V72 — « Stories » (PCC) — contenu éphémère type Instagram (Adil → membres)
-- ════════════════════════════════════════════════════════════════════
-- Un staff/owner du tenant publie une « story » (média image/vidéo + légende
-- courte + durée d'affichage + ordre) ; les membres du tenant la visionnent dans
-- un carrousel éphémère. Une story expire (expires_at) puis disparaît du feed
-- membre. Architecture tenant-agnostic : activable par n'importe quel tenant
-- whitelabel (HOMU, Restopro, OneClick) via le feature flag tenants.features.
--
-- Port du legacy Supabase (usePccStories.ts / TenantStories.tsx / StoryViewer.tsx).
-- Dans le legacy les stories surchargeaient `tenant_events` (status='actif' +
-- visible_until) ; ici on dédie une table `pcc_stories` (contenu story pur,
-- découplé des events), conforme au modèle Spring.
--
-- DIFFÉRENCES SENIOR vs legacy (toutes GÉNÉRIQUES, 0 hardcode palmeraie) :
--   • Pas de triggers SQL ni de RPC : toute la logique (scope tenant du caller,
--     soft-delete, filtre vivantes publish_at<=now / expires_at futur) vit dans
--     PccStoryService (testable, transactionnel, traçable). On garde juste les
--     contraintes/index DDL ici.
--   • « staff actif du tenant » = restaurant_staffs (soft-delete deleted_at IS
--     NULL) JOIN restaurants sur tenant_id. Read-views natives côté repo (même
--     pattern que modules/announcement isActiveStaffOfTenant + modules/feedback).
--   • Pas de bucket Storage ni de RLS : l'autorisation est portée par
--     @PreAuthorize (hasAuthority) + ABAC service (tenant-scope membre /
--     staff-scope écriture). media_url reste une string (upload via mediaService
--     existant côté front — hors scope module).
--   • Pas de tracking de vues / unread (legacy story_views) : V1 = lecture
--     simple (le client gère « vue » localement). Découpe ré-introductible plus
--     tard sans casser ce module (table dédiée séparée).
--
-- Type de média = varchar(16) + CHECK (PAS d'ENUM Postgres) : même choix que
-- announcements.priority — un ENUM natif imposerait un CAST explicite à chaque
-- INSERT/UPDATE Hibernate (friction inutile). Le CHECK garantit le même
-- invariant (image|video) au niveau base.
--
-- Workflow : créée (publish_at <= now ET expires_at NULL/futur → visible membre) ;
-- programmée (publish_at futur → invisible jusqu'à sa date) ; expirée
-- (expires_at <= now → sort du feed membre, reste visible staff pour gestion) ;
-- supprimée (soft-delete deleted_at → invisible de tous).
--
-- RBAC v2 senior strict : @PreAuthorize("hasAuthority('VERB:STORIES')") partout
-- (jamais isAuthenticated/hasRole). Grants (calque V70) :
--   • VIEW:STORIES → CLIENT, STAFF, RESTAURATEUR, GROUP_ADMIN, SUPERADMIN
--       (le membre visionne ; le staff/admin gère et voit aussi les programmées
--       + expirées). Contrairement aux annonces (B2B staff), une story est du
--       contenu DESTINÉ AU MEMBRE → CLIENT a VIEW.
--   • CREATE/UPDATE/DELETE:STORIES → RESTAURATEUR, GROUP_ADMIN, SUPERADMIN
--       (le staff de gestion qui publie). NB : STAFF (opérationnel) a VIEW mais
--       PAS d'écriture — calqué sur announcements (lecture seule).
--
-- Anti-régression : table dédiée + colonne tenants.features additive (déjà posée
-- par V70), 0 impact OneClick/HOMU/Restopro (flag défaut '{}').
--
-- ⚠️ Après application : flusher le cache userDetails (redis-cli FLUSHDB) sinon
-- les sessions en cache n'ont pas les nouvelles autorités avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- ── 1. Feature flag tenant (colonne tenants.features déjà créée par V70) ─────
-- Active la feature pour PCC (seul tenant V1 — démo Adil). Idempotent (|| merge).
UPDATE tenants
SET features = features || '{"has_stories": true}'::jsonb
WHERE slug = 'palmeraie';

-- ── 2. Table pcc_stories ─────────────────────────────────────────────────────
-- PAS de triggers : la logique (soft-delete, visibilité, ordering) est dans le SERVICE.
CREATE TABLE IF NOT EXISTS pcc_stories (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    author_id   uuid         NOT NULL REFERENCES users(id) ON DELETE SET NULL,
    media_url   text         NOT NULL,
    media_type  varchar(16)  NOT NULL DEFAULT 'image',
    caption     varchar(280),
    duration_s  int          NOT NULL DEFAULT 15,         -- durée d'affichage (sec) côté viewer
    sort_order  int          NOT NULL DEFAULT 0,          -- ordre dans le carrousel (asc)
    publish_at  timestamptz  NOT NULL DEFAULT now(),      -- programmable (futur = invisible jusqu'à sa date)
    expires_at  timestamptz,                              -- NULL = pas d'expiration ; sinon sort du feed à cette date
    deleted_at  timestamptz,                              -- soft delete (DELETE explicite staff)
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pcc_stories_media_url_chk   CHECK (char_length(media_url) BETWEEN 1 AND 2048),
    CONSTRAINT pcc_stories_caption_chk     CHECK (caption IS NULL OR char_length(caption) <= 280),
    CONSTRAINT pcc_stories_media_type_chk  CHECK (media_type IN ('image', 'video')),
    CONSTRAINT pcc_stories_duration_chk    CHECK (duration_s BETWEEN 1 AND 120)
);

-- Index « stories vivantes du tenant » (le plus chaud, au mount du carrousel
-- membre). Partiel : on ne garde que les stories non supprimées. Ordonne par
-- (tenant_id, publish_at) — le service filtre ensuite publish_at<=now /
-- expires_at futur et trie par sort_order.
CREATE INDEX IF NOT EXISTS idx_pcc_stories_tenant_publish_active
    ON pcc_stories (tenant_id, publish_at)
    WHERE deleted_at IS NULL;

-- Index gestion staff (liste complète tenant-scoped, ordre de création).
CREATE INDEX IF NOT EXISTS idx_pcc_stories_tenant_created
    ON pcc_stories (tenant_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- ════════════════════════════════════════════════════════════════════
-- 3. RBAC — ressource/menu STORIES (feuille sous Support & Tickets)
-- ════════════════════════════════════════════════════════════════════
INSERT INTO menus (code, name, parent_id, sort_order)
SELECT 'STORIES', 'Stories',
       (SELECT id FROM menus WHERE code = 'SUPPORT'), 67
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'STORIES');

-- ── 3.1 VIEW:STORIES → CLIENT + STAFF + RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ─
--    (le membre visionne le contenu ; le staff/admin gère et voit programmées + expirées)
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('CLIENT', 'STAFF', 'RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'STORIES'
  AND a.code = 'VIEW'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 3.2 CREATE:STORIES → RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ─────────────
--    (le staff de gestion qui publie ; ABAC service restreint au tenant du caller)
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'STORIES'
  AND a.code = 'CREATE'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 3.3 UPDATE:STORIES → RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ─────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'STORIES'
  AND a.code = 'UPDATE'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 3.4 DELETE:STORIES → RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ─────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'STORIES'
  AND a.code = 'DELETE'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ════════════════════════════════════════════════════════════════════
-- 4. Seed démo (idempotent) : 1 story image vivante tenant palmeraie, publiée
--    par un owner (RESTAURATEUR du tenant). Démontre un carrousel non-vide.
--    Guard NOT EXISTS sur (tenant_id, caption) → ré-exécution sûre.
-- ════════════════════════════════════════════════════════════════════
INSERT INTO pcc_stories (tenant_id, author_id, media_url, media_type, caption, duration_s, sort_order, publish_at, expires_at)
SELECT t.id,
       (SELECT u.id
        FROM users u JOIN roles r ON r.id = u.role_id
        WHERE u.tenant_id = t.id AND r.code = 'RESTAURATEUR' AND u.deleted_at IS NULL
        ORDER BY u.created_at LIMIT 1),
       'https://images.unsplash.com/photo-1554068865-24cecd4e34b8?w=1080',
       'image',
       'Bienvenue sur les stories PCC',
       15, 0, now(), now() + interval '24 hours'
FROM tenants t
WHERE t.slug = 'palmeraie'
  AND EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id = u.role_id
              WHERE u.tenant_id = t.id AND r.code = 'RESTAURATEUR' AND u.deleted_at IS NULL)
  AND NOT EXISTS (SELECT 1 FROM pcc_stories s
                  WHERE s.tenant_id = t.id AND s.caption = 'Bienvenue sur les stories PCC');
