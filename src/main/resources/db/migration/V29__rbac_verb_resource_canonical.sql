-- ============================================================================
-- V29 — RBAC canonicalisation VERB:RESOURCE (Bug 32 — Senior review)
-- ============================================================================
-- Migre les actions + menus vers la convention senior `hasAuthority('VERB:RESOURCE')`.
--
-- AVANT :
--   - actions.code : READ, SHOW, CREATE, UPDATE, DELETE, DOWNLOAD, UPLOAD
--   - menus.code   : identity, tenant, restaurant, reservation, loyalty,
--                    promotion, community, event, resource_booking, financial,
--                    payment, support
--   - Controllers utilisent @PreAuthorize("hasAnyRole('SUPERADMIN', ...)")
--
-- APRÈS :
--   - actions.code : VIEW (fusion READ+SHOW), CREATE, UPDATE, DELETE, DOWNLOAD, UPLOAD
--   - menus.code   : USERS, TENANTS, RESTAURANTS, RESERVATIONS, LOYALTY, OFFERS,
--                    COMMUNITY, EVENTS, RESOURCE_BOOKINGS, FINANCIAL, PAYMENTS,
--                    SUPPORT (+ NOTIFICATIONS, ANALYTICS, AUDIT, MEDIA nouveaux)
--   - L'authority exposée par UserRoleAuthoritiesConverter sera
--     "<action.code>:<menu.code>" = "VIEW:RESTAURANTS" (style senior)
--   - SUPERADMIN reçoit le grant total (4 verbes CRUD × 16 resources)
--   - Pilote : RESTAURATEUR/STAFF/CLIENT reçoivent leurs perms sur RESTAURANTS
--
-- Idempotent : tous les UPDATE/INSERT utilisent ON CONFLICT DO NOTHING ou WHERE
-- existing-value pour pouvoir être re-rejoués sans erreur.
-- ============================================================================

-- ─── 1. Actions : fusion READ + SHOW → VIEW ────────────────────────────────
-- Pourquoi : le pattern senior groupe getAll/getById/search sous le même verbe
-- "VIEW" (list+detail+search = même nature "lire"). On unifie pour aligner.

-- 1.a Avant de DELETE 'SHOW', déplacer ses permissions vers ce qui deviendra
-- 'VIEW' (= l'actuel 'READ'). ON CONFLICT évite les doublons (un rôle peut déjà
-- avoir READ ET SHOW sur le même menu).
INSERT INTO permissions (id, role_id, menu_id, action_id, created_at)
SELECT gen_random_uuid(),
       p.role_id,
       p.menu_id,
       (SELECT id FROM actions WHERE code = 'READ'),
       NOW()
  FROM permissions p
 WHERE p.action_id = (SELECT id FROM actions WHERE code = 'SHOW')
ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;

-- 1.b Supprimer l'action 'SHOW' (cascade supprime les permissions liées qui
-- pointaient vers SHOW — celles déplacées vers READ subsistent).
DELETE FROM actions WHERE code = 'SHOW';

-- 1.c Renommer 'READ' → 'VIEW' (les FK permissions.action_id sont des UUID,
-- ne dépendent pas du code → no-op pour les permissions).
UPDATE actions SET code = 'VIEW', name = 'Voir' WHERE code = 'READ';

-- ─── 2. Menus : rename → pluriel UPPERCASE anglais ─────────────────────────
-- Convention senior : VIEW:RESTAURANTS (pluriel anglais). On renomme les
-- menu.code existants ; les UUID + FK permissions.menu_id sont préservés.

UPDATE menus SET code = 'USERS'             WHERE code = 'identity';
UPDATE menus SET code = 'TENANTS'           WHERE code = 'tenant';
UPDATE menus SET code = 'RESTAURANTS'       WHERE code = 'restaurant';
UPDATE menus SET code = 'RESERVATIONS'      WHERE code = 'reservation';
UPDATE menus SET code = 'LOYALTY'           WHERE code = 'loyalty';
UPDATE menus SET code = 'OFFERS'            WHERE code = 'promotion';
UPDATE menus SET code = 'COMMUNITY'         WHERE code = 'community';
UPDATE menus SET code = 'EVENTS'            WHERE code = 'event';
UPDATE menus SET code = 'RESOURCE_BOOKINGS' WHERE code = 'resource_booking';
UPDATE menus SET code = 'FINANCIAL'         WHERE code = 'financial';
UPDATE menus SET code = 'PAYMENTS'          WHERE code = 'payment';
UPDATE menus SET code = 'SUPPORT'           WHERE code = 'support';

-- ─── 3. Menus : seed des 4 RESOURCES manquantes ────────────────────────────
-- Ces resources existent dans le code (controllers) mais n'avaient pas
-- d'entrée menus pour le RBAC. On les ajoute pour pouvoir grant des perms.

INSERT INTO menus (id, code, name, sort_order) VALUES
  (gen_random_uuid(), 'NOTIFICATIONS', 'Notifications',     130),
  (gen_random_uuid(), 'ANALYTICS',     'Analytics & Stats', 140),
  (gen_random_uuid(), 'AUDIT',         'Audit & Logs',      150),
  (gen_random_uuid(), 'MEDIA',         'Media & Uploads',   160)
ON CONFLICT (code) DO NOTHING;

-- ─── 4. SUPERADMIN reçoit le grant total ───────────────────────────────────
-- Sans ce grant, le SUPERADMIN serait bloqué sur les routes migrées vers
-- @PreAuthorize("hasAuthority('VIEW:RESTAURANTS')") car le converter n'expose
-- que des authorities issues de la table permissions.
--
-- Cross-join (toutes resources × tous verbes) — idempotent ON CONFLICT.
-- Note : on grant aussi DOWNLOAD/UPLOAD pour rester cohérent avec les
-- permissions existantes seedées (ex: media), bien qu'elles soient
-- hors-CRUD-strict du pilote.

INSERT INTO permissions (id, role_id, menu_id, action_id, created_at)
SELECT gen_random_uuid(),
       (SELECT id FROM roles WHERE code = 'SUPERADMIN'),
       m.id,
       a.id,
       NOW()
  FROM menus m
 CROSS JOIN actions a
ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;

-- ─── 5. Pilote : RESTAURATEUR / STAFF / CLIENT sur RESTAURANTS ─────────────
-- Permissions minimales pour le controller pilote (RestaurantController).
-- Les autres resources continueront via le double-binding `hasAnyRole(...)`
-- pendant la transition (cf. étape 4 du plan migration).
--
-- Scoping fin (own restaurants only pour RESTAURATEUR) reste géré par
-- SecurityHelper.requireOwnerOrAdmin dans les services — la permission ici
-- est juste l'autorisation grossière (RBAC).

-- RESTAURATEUR : VIEW + UPDATE explicites sur RESTAURANTS pour garantir le
-- pilote. Note : la DB pré-V29 grant déjà CREATE/DELETE/DOWNLOAD/UPLOAD
-- (seed RBAC initial) — on ne révoque rien ici. Le scoping ownership (un
-- owner ne peut DELETE que ses propres restos) reste dans le service via
-- SecurityHelper.requireOwnerOrAdmin → RBAC autorise, ABAC restreint.
INSERT INTO permissions (id, role_id, menu_id, action_id, created_at)
SELECT gen_random_uuid(),
       (SELECT id FROM roles WHERE code = 'RESTAURATEUR'),
       (SELECT id FROM menus WHERE code = 'RESTAURANTS'),
       a.id,
       NOW()
  FROM actions a
 WHERE a.code IN ('VIEW', 'UPDATE')
ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;

-- GROUP_ADMIN : VIEW + CREATE + UPDATE + DELETE sur RESTAURANTS (admin d'un
-- groupe peut tout faire dans son périmètre — scope tenant_id géré dans service).
INSERT INTO permissions (id, role_id, menu_id, action_id, created_at)
SELECT gen_random_uuid(),
       (SELECT id FROM roles WHERE code = 'GROUP_ADMIN'),
       (SELECT id FROM menus WHERE code = 'RESTAURANTS'),
       a.id,
       NOW()
  FROM actions a
 WHERE a.code IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE')
ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;

-- STAFF : VIEW only (consulter son resto employeur).
INSERT INTO permissions (id, role_id, menu_id, action_id, created_at)
SELECT gen_random_uuid(),
       (SELECT id FROM roles WHERE code = 'STAFF'),
       (SELECT id FROM menus WHERE code = 'RESTAURANTS'),
       (SELECT id FROM actions WHERE code = 'VIEW'),
       NOW()
ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;

-- CLIENT : VIEW only (catalogue public — Compass/Spotlight).
INSERT INTO permissions (id, role_id, menu_id, action_id, created_at)
SELECT gen_random_uuid(),
       (SELECT id FROM roles WHERE code = 'CLIENT'),
       (SELECT id FROM menus WHERE code = 'RESTAURANTS'),
       (SELECT id FROM actions WHERE code = 'VIEW'),
       NOW()
ON CONFLICT (role_id, menu_id, action_id) DO NOTHING;

-- Note : COMMENT ON TABLE volontairement omis car le user applicatif
-- (oneclick_app) n'est pas owner de la table permissions (créée par hh) et
-- échouerait sur ALTER COMMENT. La documentation RBAC v2 est en tête du
-- fichier — suffisante pour git blame + lecture humaine.
