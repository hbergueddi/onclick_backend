-- ════════════════════════════════════════════════════════════════════
-- V34 — Self-access NOTIFICATIONS pour tous les rôles
-- ════════════════════════════════════════════════════════════════════
-- Avant : seul SUPERADMIN avait des permissions NOTIFICATIONS → la cloche
-- (liste/badge/mark-read) et l'enregistrement du token push renvoyaient 403
-- pour CLIENT/RESTAURATEUR/STAFF/GROUP_ADMIN. Modèle RBAC senior = hasAuthority
-- (pas de isAuthenticated) + ownership imposé côté service.
--
-- On accorde VIEW + UPDATE:NOTIFICATIONS à tous les rôles (hors SUPERADMIN qui
-- a déjà tout). Sécurité préservée :
--   • by-user / unread-count / tokens-by-user / markRead / mark-all-read :
--     SecurityHelper.requireOwnerOrAdmin → chaque user ne voit/modifie que SES
--     notifications.
--   • findAll (liste paginée) : scopée au self pour les non-admins (service).
--   • registerToken (POST /tokens) : gardé sous UPDATE (upsert idempotent) +
--     owner-check ; un user n'enregistre que son propre token.
-- CREATE / DELETE:NOTIFICATIONS restent réservés à SUPERADMIN (création de
-- notifications/campagnes admin, suppression de tokens tiers).
-- Idempotent (NOT EXISTS) — même pattern que V33.
-- ════════════════════════════════════════════════════════════════════

INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE m.code = 'NOTIFICATIONS'
  AND a.code IN ('VIEW', 'UPDATE')
  AND r.code <> 'SUPERADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
