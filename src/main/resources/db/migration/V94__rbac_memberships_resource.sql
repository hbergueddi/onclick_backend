-- ════════════════════════════════════════════════════════════════════════
-- V94 — Ressource RBAC MEMBERSHIPS (P2 — invitation admin tenant)
-- ════════════════════════════════════════════════════════════════════════
-- Nouvelle ressource `MEMBERSHIPS` + grants pour l'endpoint
--   POST /api/tenants/{tenantId}/members  (CREATE:MEMBERSHIPS)
-- L'admin du tenant (depuis tenant/{slug}) invite une personne dans son programme :
-- réutilise/crée UN compte OneClick + ajoute une membership (modèle additif P0/P1).
--
-- Qui détient l'autorité : les rôles « admin de tenant ».
--   - RESTAURATEUR : les admins PCC/HOMU sont des RESTAURATEUR home-tenant=<programme>
--     (vérifié : palmeraie = 10 RESTAURATEUR, 0 GROUP_ADMIN ; homu = 1 GROUP_ADMIN + 5 RESTAURATEUR).
--   - GROUP_ADMIN + SUPERADMIN : admins multi-resto / plateforme.
-- Le GATE réel reste l'ABAC own-tenant côté service (MembershipInviteService) : un admin ne
-- peut inviter QUE dans son tenant home (sauf SUPERADMIN, bypass via DELETE:TENANTS exclusif).
-- Pattern idempotent NOT EXISTS (cf V62/V70). created_at/updated_at/id : defaults DB.

-- 1) Menu/ressource (sous ADMINISTRATION).
INSERT INTO menus (code, name, parent_id, sort_order)
SELECT 'MEMBERSHIPS', 'Membres (programme)',
       (SELECT id FROM menus WHERE code = 'ADMINISTRATION'), 60
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'MEMBERSHIPS');

-- 2) Grant CREATE aux rôles admin de tenant (l'invite POST). VIEW:MEMBERSHIPS
--    (liste membres enrichie + KPIs STOMP) sera ajouté en P3 avec son endpoint — on
--    ne seed que l'autorité réellement câblée ici.
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'MEMBERSHIPS'
  AND a.code = 'CREATE'
  AND NOT EXISTS (
      SELECT 1 FROM permissions p
      WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
