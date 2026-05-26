-- ════════════════════════════════════════════════════════════════════
-- V38 — Grant CREATE/UPDATE/DELETE:COMMUNITY au rôle CLIENT
-- ════════════════════════════════════════════════════════════════════
-- Le CLIENT ne détenait que COMMUNITY:VIEW. Or TOUTES les écritures sociales
-- (SocialController) sont gardées par CREATE/UPDATE/DELETE:COMMUNITY :
--   • favoris (POST/DELETE /api/social/favorites)         → ❤️ Compass/Spotlight
--   • amitiés (POST /friendships, PATCH accept/decline)   → ajout/accept d'ami
--   • squads  (POST/PATCH/DELETE /friend-groups + membres)→ groupes d'amis
--   • parrainages (POST /referrals, PATCH activate)       → referral
-- → sans ces droits, le CLIENT (utilisateur PRINCIPAL de ces features Pocket)
--   prenait un 403 silencieux sur chacune. C'est un trou de la matrice RBAC.
--
-- L'octroi est SÛR car le scoping fin (ownership) est déjà fait côté service :
--   • favoris : requireOwnerOrAdmin(userId)
--   • amitié  : request() exige caller ∈ parties (durci, commit a3b41e7) ;
--               accept/decline exigent une partie
--   • squads  : create → owner = current ; update/delete → requireOwnerOrAdmin ;
--               membres → admin/owner du groupe
--   • referral: create → requireOwnerOrAdmin(referrerId) (durci a3b41e7)
--
-- Idempotent (NOT EXISTS) — même pattern que V34/V35/V37.
-- ⚠️ Après application : flusher le cache userDetails (Redis) sinon les CLIENT
-- déjà en cache ne voient pas les nouveaux droits avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE r.code = 'CLIENT'
  AND m.code = 'COMMUNITY'
  AND a.code IN ('CREATE', 'UPDATE', 'DELETE')
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
