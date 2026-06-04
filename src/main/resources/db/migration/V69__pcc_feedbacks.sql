-- ════════════════════════════════════════════════════════════════════
-- V69 — « Avis » (PCC Lot 7) — thread membre ↔ owner (Adil)
-- ════════════════════════════════════════════════════════════════════
-- Un membre envoie un avis (sentiment happy/unhappy + catégorie libre + commentaire
-- optionnel), éventuellement ciblé sur un resto précis du tenant (target_restaurant_id)
-- ou général (NULL = tenant-wide). L'owner du resto ciblé (ou un admin/tenant-admin)
-- répond UNE fois ; le membre marque la réponse lue. Le thread est conservé.
--
-- Port fidèle du legacy Supabase :
--   • 20260427160000_pcc_feedbacks_thread.sql (table + RLS members/owners/admin)
--   • 20260430140000_pcc_feedbacks_target_restaurant.sql (col target_restaurant_id + scoping)
--   • Edge Functions send-pcc-feedback (create + notif) / send-pcc-feedback-reply (reply + notif)
-- vers un module Spring CLOSED `modules/feedback`.
--
-- DIFFÉRENCE GÉNÉRIQUE vs legacy (PCC-only hardcodé tenant=palmeraie) :
--   Ici tenant_id = celui DU CALLER (résolu via UserDirectoryApi côté service), pas un
--   UUID palmeraie en dur. L'owner-scoping (lecture inbox owner) passe par une read-view
--   SQL native joignant restaurant_staffs/restaurants (schéma Spring : role_code='owner',
--   soft-delete deleted_at IS NULL), pas par t.slug='palmeraie'. Réutilisable par tout
--   tenant whitelabel (HOMU, Restopro, …) sans modification.
--
-- Workflow status : created (reply_at NULL) → replied (reply_text/by/at posés) ;
-- le membre passe reply_read_by_member à true à la lecture. ABAC dans PccFeedbackService :
--   • membre voit/marque-lu UNIQUEMENT ses propres avis (member_id = caller) ;
--   • owner voit/répond aux avis ciblant SES restos + les généraux (target NULL) ;
--   • admin (GROUP_ADMIN/SUPERADMIN) voit/répond à tout.
--
-- RBAC v2 senior strict : @PreAuthorize("hasAuthority('VERB:FEEDBACK')") partout
-- (jamais isAuthenticated/hasRole). Grants (calque V66/V68) :
--   • CREATE:FEEDBACK → CLIENT (le membre envoie un avis)
--   • VIEW:FEEDBACK   → CLIENT, STAFF, RESTAURATEUR, GROUP_ADMIN, SUPERADMIN
--       (membre lit les siens via /mine ; staff/admin lisent l'inbox owner-scopée via /inbox)
--   • UPDATE:FEEDBACK → CLIENT (mark-reply-read) + RESTAURATEUR, GROUP_ADMIN, SUPERADMIN (reply)
--       NB : STAFF n'a PAS UPDATE:FEEDBACK — seul un owner/admin répond aux avis (legacy :
--       reply réservé owner PCC / tenant-admin / super-admin). Le mark-read côté membre est
--       gardé par UPDATE:FEEDBACK + ABAC member_id=caller. La distinction reply vs mark-read
--       est portée par le service (isStaffOrAdmin pour reply).
--
-- Anti-régression : table dédiée, 0 impact OneClick/HOMU/Restopro.
--
-- ⚠️ Après application : flusher le cache userDetails (redis-cli FLUSHDB) sinon les
-- sessions en cache n'ont pas les nouvelles autorités avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- ── 1. Table pcc_feedbacks ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS pcc_feedbacks (
    id                    uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id             uuid          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id             uuid          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sentiment             varchar(16)   NOT NULL,
    category              varchar(128)  NOT NULL,
    comment               varchar(2000),
    -- resto PCC cible (Padel/Spa/Golf/etc.) ; NULL = avis général tenant-wide
    target_restaurant_id  uuid          REFERENCES restaurants(id) ON DELETE SET NULL,
    -- réponse de l'owner (Adil) — posée une seule fois (workflow created → replied)
    reply_text            varchar(2000),
    reply_by              uuid          REFERENCES users(id) ON DELETE SET NULL,
    reply_at              timestamptz,
    -- suivi de lecture côté membre
    reply_read_by_member  boolean       NOT NULL DEFAULT false,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pcc_feedbacks_sentiment_chk CHECK (sentiment IN ('happy', 'unhappy'))
);

-- Lecture « mes avis » du membre (caller = member_id), du plus récent au plus ancien.
CREATE INDEX IF NOT EXISTS idx_pcc_feedbacks_member
    ON pcc_feedbacks (member_id, created_at DESC);

-- Filtre owner-scope par resto ciblé (read-view inbox owner). Partiel : seuls les avis
-- ciblés portent un target ; les généraux (NULL) sont récupérés par le OR target IS NULL.
CREATE INDEX IF NOT EXISTS idx_pcc_feedbacks_target_restaurant
    ON pcc_feedbacks (target_restaurant_id)
    WHERE target_restaurant_id IS NOT NULL;

-- Scope tenant (inbox owner/admin filtrée au tenant du caller).
CREATE INDEX IF NOT EXISTS idx_pcc_feedbacks_tenant
    ON pcc_feedbacks (tenant_id, created_at DESC);

-- ── 2. Ressource RBAC FEEDBACK (feuille sous Support & Tickets) ─────────────
INSERT INTO menus (code, name, parent_id, sort_order)
SELECT 'FEEDBACK', 'Avis membres',
       (SELECT id FROM menus WHERE code = 'SUPPORT'), 65
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'FEEDBACK');

-- ── 3. CREATE:FEEDBACK → CLIENT (le membre envoie un avis) ──────────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code = 'CLIENT'
  AND m.code = 'FEEDBACK'
  AND a.code = 'CREATE'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 4. VIEW:FEEDBACK → CLIENT + STAFF + RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ─
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('CLIENT', 'STAFF', 'RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'FEEDBACK'
  AND a.code = 'VIEW'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 5. UPDATE:FEEDBACK → CLIENT (mark-read) + RESTAURATEUR + GROUP_ADMIN + SUPERADMIN (reply) ─
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('CLIENT', 'RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'FEEDBACK'
  AND a.code = 'UPDATE'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ════════════════════════════════════════════════════════════════════
-- 6. Seed démo (idempotent) : member1 — 1 avis unhappy général + 1 happy ciblé.
--    Utile pour démontrer une inbox owner non-vide. Guards NOT EXISTS sur la paire
--    (member_id, sentiment, category) → ré-exécution sûre (la table n'a pas d'UNIQUE).
-- ════════════════════════════════════════════════════════════════════
INSERT INTO pcc_feedbacks (member_id, tenant_id, sentiment, category, comment, target_restaurant_id)
SELECT m1.id, m1.tenant_id, 'unhappy', 'Propreté', 'Les vestiaires du Padel étaient sales hier soir.', NULL
FROM users m1
WHERE m1.email = 'member1@palmeraie.com'
  AND m1.tenant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM pcc_feedbacks f
      WHERE f.member_id = m1.id AND f.sentiment = 'unhappy' AND f.category = 'Propreté');

INSERT INTO pcc_feedbacks (member_id, tenant_id, sentiment, category, comment, target_restaurant_id)
SELECT m1.id, m1.tenant_id, 'happy', 'Accueil',
       'Le coach du Palm Gym était super pro, merci !',
       (SELECT r.id FROM restaurants r WHERE r.tenant_id = m1.tenant_id AND r.name = 'Palm Gym' LIMIT 1)
FROM users m1
WHERE m1.email = 'member1@palmeraie.com'
  AND m1.tenant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM pcc_feedbacks f
      WHERE f.member_id = m1.id AND f.sentiment = 'happy' AND f.category = 'Accueil');
