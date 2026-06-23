-- ════════════════════════════════════════════════════════════════════
-- V103 — Authority self-service DELETE:PROFILE (suppression de SON propre compte)
-- ════════════════════════════════════════════════════════════════════
-- App Store Review Guideline §5.1.1(v) + RGPD « droit à l'effacement » : tout user
-- authentifié doit pouvoir supprimer son compte depuis l'app, sans friction.
--
-- RBAC v2 strict : DELETE /api/users/me est gardé par hasAuthority('DELETE:PROFILE')
-- (≠ DELETE:USERS, l'action ADMIN sur les AUTRES users). On accorde DELETE sur la
-- ressource self-service PROFILE (créée en V41) à TOUS les rôles — chacun possède un
-- profil supprimable. Le scope « soi-même » est garanti par construction (JWT.sub) côté
-- contrôleur, pas d'ABAC. L'action 'DELETE' existe déjà (utilisée par DELETE:USERS).
--
-- Idempotent (NOT EXISTS). ⚠️ Après application : flusher le cache userDetails (Redis)
-- sinon les sessions déjà en cache n'ont pas DELETE:PROFILE jusqu'au TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE m.code = 'PROFILE'
  AND a.code = 'DELETE'
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
