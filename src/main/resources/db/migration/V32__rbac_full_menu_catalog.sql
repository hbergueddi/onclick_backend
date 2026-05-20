-- ════════════════════════════════════════════════════════════════════
-- V32 — Catalogue RBAC complet & hiérarchique + seed des permissions
-- ════════════════════════════════════════════════════════════════════
-- Retour dev senior : « beaucoup de permissions manquantes ». Cause : seules
-- 16 ressources (menus plats) étaient seedées alors que les 34 controllers en
-- exposent ~40 → des endpoints retombaient sur hasAnyRole/isAuthenticated.
--
-- Cette migration installe le catalogue cible : 7 catégories parentes + un
-- DASHBOARD autonome + 15 nouvelles ressources, et re-parente les 16 existantes
-- → arborescence (parents → enfants) exploitée par la page « Gérer les
-- permissions ». Verbes inchangés (CREATE/VIEW/UPDATE/DELETE/UPLOAD/DOWNLOAD).
--
-- Seed (sans régression d'accès) :
--   - SUPERADMIN : 6 actions sur toutes les FEUILLES (catégories exclues).
--   - Sous-ressources resto (ZONES/TABLES/STAFF/SERVICES) : VIEW pour tous les
--     rôles (préserve les GET isAuthenticated) + CREATE/UPDATE/DELETE pour
--     GROUP_ADMIN/RESTAURATEUR (préserve les hasAnyRole convertis en Stage 2).
-- Les catégories parentes ne portent PAS de permission (entêtes de navigation).
-- ════════════════════════════════════════════════════════════════════

-- ── 1. Catégories parentes (navigation, sans permission directe) ────────────
INSERT INTO menus (code, name, sort_order, icon) VALUES
  ('RESTAURATION',     'Restauration',   10, 'utensils'),
  ('RESERVATION_MGMT', 'Réservations',   20, 'calendar'),
  ('FIDELITE',         'Fidélité',       30, 'gift'),
  ('FINANCE',          'Finance',        40, 'wallet'),
  ('SOCIAL_HUB',       'Social',         50, 'users'),
  ('COMMUNICATION',    'Communication',  60, 'bell'),
  ('ADMINISTRATION',   'Administration', 70, 'shield');

-- ── 2. Tableau de bord (feuille autonome, racine) ───────────────────────────
INSERT INTO menus (code, name, sort_order, path) VALUES
  ('DASHBOARD', 'Tableau de bord', 0, '/dashboard');

-- ── 3. Nouvelles ressources (feuilles) rattachées à leur catégorie ──────────
INSERT INTO menus (code, name, parent_id, sort_order) VALUES
  ('ZONES',            'Zones',            (SELECT id FROM menus WHERE code='RESTAURATION'),     12),
  ('TABLES',           'Tables',           (SELECT id FROM menus WHERE code='RESTAURATION'),     13),
  ('STAFF',            'Personnel',        (SELECT id FROM menus WHERE code='RESTAURATION'),     14),
  ('SERVICES',         'Services repas',   (SELECT id FROM menus WHERE code='RESTAURATION'),     15),
  ('EXPLORE_FEATURED', 'Mise en avant',    (SELECT id FROM menus WHERE code='RESTAURATION'),     16),
  ('WALLET_PASS',      'Wallet Pass',      (SELECT id FROM menus WHERE code='FIDELITE'),         33),
  ('ENROLLMENTS',      'Enrôlements',      (SELECT id FROM menus WHERE code='FIDELITE'),         34),
  ('ONECLICK_HI',      'OneClick HI',      (SELECT id FROM menus WHERE code='FINANCE'),          43),
  ('SOCIAL',           'Parrainage & amis',(SELECT id FROM menus WHERE code='SOCIAL_HUB'),       52),
  ('PROMO_REQUESTS',   'Demandes promo',   (SELECT id FROM menus WHERE code='COMMUNICATION'),    62),
  ('EMAIL',            'Emails',           (SELECT id FROM menus WHERE code='COMMUNICATION'),    63),
  ('CONFIGURATION',    'Configuration',    (SELECT id FROM menus WHERE code='ADMINISTRATION'),   73),
  ('SYSTEM',           'Système',          (SELECT id FROM menus WHERE code='ADMINISTRATION'),   74),
  ('AI',               'Assistant IA',     (SELECT id FROM menus WHERE code='ADMINISTRATION'),   77),
  ('STORE_ONBOARDING', 'Onboarding Store', (SELECT id FROM menus WHERE code='ADMINISTRATION'),   78);

-- ── 4. Re-parentage des 16 menus existants ──────────────────────────────────
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='RESTAURATION'),     sort_order=11 WHERE code='RESTAURANTS';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='RESERVATION_MGMT'), sort_order=21 WHERE code='RESERVATIONS';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='RESERVATION_MGMT'), sort_order=22 WHERE code='RESOURCE_BOOKINGS';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='FIDELITE'),         sort_order=31 WHERE code='LOYALTY';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='FIDELITE'),         sort_order=32 WHERE code='OFFERS';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='FINANCE'),          sort_order=41 WHERE code='FINANCIAL';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='FINANCE'),          sort_order=42 WHERE code='PAYMENTS';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='SOCIAL_HUB'),       sort_order=51 WHERE code='COMMUNITY';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='SOCIAL_HUB'),       sort_order=53 WHERE code='EVENTS';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='COMMUNICATION'),    sort_order=61 WHERE code='NOTIFICATIONS';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='COMMUNICATION'),    sort_order=64 WHERE code='SUPPORT';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='ADMINISTRATION'),   sort_order=71 WHERE code='USERS';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='ADMINISTRATION'),   sort_order=72 WHERE code='TENANTS';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='ADMINISTRATION'),   sort_order=75 WHERE code='AUDIT';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='ADMINISTRATION'),   sort_order=76 WHERE code='MEDIA';
UPDATE menus SET parent_id=(SELECT id FROM menus WHERE code='ADMINISTRATION'),   sort_order=79 WHERE code='ANALYTICS';

-- ── 5. Seed des permissions ─────────────────────────────────────────────────
-- 5a. SUPERADMIN : 6 actions × toutes les FEUILLES (catégories exclues)
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code = 'SUPERADMIN'
  AND m.code NOT IN ('RESTAURATION','RESERVATION_MGMT','FIDELITE','FINANCE','SOCIAL_HUB','COMMUNICATION','ADMINISTRATION')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- 5b. Sous-ressources resto : VIEW pour TOUS les rôles (préserve GET isAuthenticated)
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE m.code IN ('ZONES','TABLES','STAFF','SERVICES')
  AND a.code = 'VIEW'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- 5c. Sous-ressources resto : CREATE/UPDATE/DELETE pour GROUP_ADMIN + RESTAURATEUR (préserve hasAnyRole)
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('GROUP_ADMIN','RESTAURATEUR')
  AND m.code IN ('ZONES','TABLES','STAFF','SERVICES')
  AND a.code IN ('CREATE','UPDATE','DELETE')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);
