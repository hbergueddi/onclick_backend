-- ════════════════════════════════════════════════════════════════════
-- V73 — « Séminaires » (PCC) — demandes de devis B2B (membre → commercial)
-- ════════════════════════════════════════════════════════════════════
-- Un membre soumet une demande de devis pour un séminaire / événement d'entreprise
-- (entreprise, contact, nombre de participants, dates souhaitées, besoins). Le commercial
-- (staff/admin du tenant) traite la demande via un workflow de statut :
--   demandee → en_traitement → devis_envoye → confirmee | refusee | annulee
-- À chaque changement de statut, le membre organisateur est notifié (in-app + push).
--
-- Port fidèle du legacy Supabase :
--   • 20260424140000_pcc_seminars_events.sql (table seminar_requests + RLS members/staff/admin
--     + RPC create_seminar_request + RPC update_seminar_status)
--   • Edge Function send-pcc-seminar-status-update (notif organisateur par transition de statut)
-- vers un module Spring CLOSED `modules/seminar`.
--
-- DIFFÉRENCE GÉNÉRIQUE vs legacy (PCC-only hardcodé tenant=palmeraie) :
--   tenant_id = celui DU CALLER (résolu via UserDirectoryApi côté service), pas un UUID
--   palmeraie en dur. Le staff-scope (inbox commercial) est tenant-wide (tout staff/admin actif
--   du tenant voit toutes les demandes du tenant), pas restreint par resto ni par slug.
--   Réutilisable par tout tenant whitelabel (HOMU, Restopro, …) sans modification.
--
-- ABAC (PccSeminarService) :
--   • membre : crée une demande (organizer_id = caller, tenant_id = tenant du caller) ;
--     lit UNIQUEMENT ses propres demandes (/mine).
--   • staff/admin : lit toutes les demandes de SON tenant (/inbox) + change le statut
--     (+ notes internes). STAFF inclus (le commercial peut être un staff opérationnel).
--
-- RBAC v2 senior strict : @PreAuthorize("hasAuthority('VERB:SEMINARS')") partout
-- (jamais isAuthenticated/hasRole). Grants (calque V69) :
--   • CREATE:SEMINARS → CLIENT (le membre soumet une demande)
--   • VIEW:SEMINARS   → CLIENT, STAFF, RESTAURATEUR, GROUP_ADMIN, SUPERADMIN
--   • UPDATE:SEMINARS → STAFF, RESTAURATEUR, GROUP_ADMIN, SUPERADMIN (le commercial traite)
--       NB : CLIENT n'a PAS UPDATE:SEMINARS (legacy : le membre ne change pas le statut ;
--       l'annulation côté membre n'était pas exposée — gardé à l'identique).
--
-- Anti-régression : table dédiée, 0 impact OneClick/HOMU/Restopro. Extension additive du
-- CHECK notifications.type (ajout 'seminar' — calque V39 friend_request).
--
-- ⚠️ Après application : flusher le cache userDetails (redis-cli FLUSHDB) sinon les
-- sessions en cache n'ont pas les nouvelles autorités avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- ── 1. Table seminar_requests ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS seminar_requests (
    id                    uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             uuid          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    -- membre organisateur (le caller). SET NULL si le compte est supprimé (la demande reste
    -- pour l'historique commercial), comme le legacy (organizer_id ON DELETE SET NULL).
    organizer_id          uuid          REFERENCES users(id) ON DELETE SET NULL,
    company_name          varchar(255)  NOT NULL,
    contact_name          varchar(255)  NOT NULL,
    contact_email         varchar(320)  NOT NULL,
    contact_phone         varchar(40),
    expected_attendees    integer       NOT NULL DEFAULT 10,
    preferred_date_start  date,
    preferred_date_end    date,
    needs_text            varchar(4000),
    status                varchar(32)   NOT NULL DEFAULT 'demandee',
    -- notes internes commercial (non visibles côté membre dans l'UI ; champ mutable)
    notes_internal        varchar(4000),
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT seminar_requests_attendees_chk CHECK (expected_attendees >= 1),
    CONSTRAINT seminar_requests_status_chk CHECK (
        status IN ('demandee', 'en_traitement', 'devis_envoye', 'confirmee', 'refusee', 'annulee'))
);

-- Inbox commercial : demandes d'un tenant par statut, du plus récent au plus ancien.
CREATE INDEX IF NOT EXISTS idx_seminar_requests_tenant_status
    ON seminar_requests (tenant_id, status, created_at DESC);

-- « Mes demandes » du membre (caller = organizer_id), du plus récent au plus ancien.
CREATE INDEX IF NOT EXISTS idx_seminar_requests_organizer
    ON seminar_requests (organizer_id, created_at DESC);

-- ── 2. Extension CHECK notifications.type (ajout 'seminar') ──────────────────
-- Calque V39 (DROP+ADD idempotent). La notif serveur de changement de statut séminaire
-- porte type='seminar' ; sans cette valeur le CHECK V39 la rejetterait (23514) et la notif
-- serait silencieusement perdue (try/catch du NotificationEventHandler).
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('reservation', 'loyalty', 'promotion', 'community',
                    'support', 'system', 'announcement', 'friend_request', 'seminar'));

-- ── 3. Ressource RBAC SEMINARS (feuille sous Support & Tickets) ─────────────
INSERT INTO menus (code, name, parent_id, sort_order)
SELECT 'SEMINARS', 'Séminaires',
       (SELECT id FROM menus WHERE code = 'SUPPORT'), 67
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'SEMINARS');

-- ── 4. CREATE:SEMINARS → CLIENT (le membre soumet une demande) ──────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code = 'CLIENT'
  AND m.code = 'SEMINARS'
  AND a.code = 'CREATE'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 5. VIEW:SEMINARS → CLIENT + STAFF + RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ─
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('CLIENT', 'STAFF', 'RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'SEMINARS'
  AND a.code = 'VIEW'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 6. UPDATE:SEMINARS → STAFF + RESTAURATEUR + GROUP_ADMIN + SUPERADMIN ─────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('STAFF', 'RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'SEMINARS'
  AND a.code = 'UPDATE'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ════════════════════════════════════════════════════════════════════
-- 7. Seed démo (idempotent) : 1 demande de séminaire de member1 (statut demandee).
--    Utile pour démontrer une inbox commercial non-vide. Guard NOT EXISTS sur la paire
--    (organizer_id, company_name) → ré-exécution sûre.
-- ════════════════════════════════════════════════════════════════════
INSERT INTO seminar_requests (
    tenant_id, organizer_id, company_name, contact_name, contact_email, contact_phone,
    expected_attendees, preferred_date_start, preferred_date_end, needs_text, status)
SELECT m1.tenant_id, m1.id, 'Atlas Conseil', 'Karim Benali', 'karim.benali@atlas-conseil.ma', '+212600112233',
       40, (now() + interval '30 day')::date, (now() + interval '31 day')::date,
       'Séminaire annuel — salle plénière 40 pers., déjeuner sur place, pause-café x2.', 'demandee'
FROM users m1
WHERE m1.email = 'member1@palmeraie.com'
  AND m1.tenant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM seminar_requests s
      WHERE s.organizer_id = m1.id AND s.company_name = 'Atlas Conseil');
