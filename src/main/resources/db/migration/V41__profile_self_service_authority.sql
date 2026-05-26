-- ════════════════════════════════════════════════════════════════════
-- V41 — Ressource RBAC « PROFILE » : self-service du PROPRE profil
-- ════════════════════════════════════════════════════════════════════
-- RBAC v2 senior strict : @PreAuthorize doit toujours être hasAuthority('VERB:RESOURCE'),
-- JAMAIS isAuthenticated(). Or la famille /api/users/me (GET /me, /me/permissions,
-- /me/context, PATCH /me, POST /me/password) est du self-service : tout user authentifié
-- gère SON propre profil — distinct du menu USERS (admin gérant les AUTRES users).
--
-- On crée donc une ressource dédiée PROFILE :
--   • VIEW:PROFILE   → lire son profil/contexte (GET /me*)
--   • UPDATE:PROFILE → éditer son profil + son mot de passe (PATCH /me, POST /me/password)
-- accordée à TOUS les rôles (chacun a un profil). Le scoping « soi-même » est garanti par
-- construction côté contrôleur (resolution via JWT.sub), pas besoin d'ABAC.
--
-- Idempotent (NOT EXISTS). ⚠️ Après application : flusher le cache userDetails (Redis)
-- sinon les sessions déjà en cache n'ont pas VIEW:PROFILE → GET /me/context (bootstrap
-- au démarrage de l'app) renverrait 403 jusqu'au TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- 1) Menu PROFILE (ressource self-service ; pas une entrée de sidebar → path/parent NULL).
INSERT INTO menus (id, code, name, icon, sort_order)
SELECT gen_random_uuid(), 'PROFILE', 'Mon profil', 'user', 900
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'PROFILE');

-- 2) Grant VIEW:PROFILE + UPDATE:PROFILE à TOUS les rôles.
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE m.code = 'PROFILE'
  AND a.code IN ('VIEW', 'UPDATE')
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
