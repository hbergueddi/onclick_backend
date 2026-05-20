-- ════════════════════════════════════════════════════════════════════
-- V33 — Menu « ROLES » (page Gérer les permissions) + perms SUPERADMIN
-- ════════════════════════════════════════════════════════════════════
-- La page d'administration des permissions est elle-même une ressource RBAC :
-- protégée par hasAuthority('VIEW:ROLES') / hasAuthority('UPDATE:ROLES').
-- Réservée à SUPERADMIN au seed (l'admin peut l'ouvrir à d'autres via l'UI).
-- ════════════════════════════════════════════════════════════════════

INSERT INTO menus (code, name, parent_id, sort_order) VALUES
  ('ROLES', 'Gérer les permissions', (SELECT id FROM menus WHERE code='ADMINISTRATION'), 70);

INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code = 'SUPERADMIN' AND m.code = 'ROLES'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);
