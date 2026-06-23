-- ════════════════════════════════════════════════════════════════════════
-- V99 — Grant VIEW:LOYALTY_TIER aux admins de tenant (lecture seule des paliers)
-- ════════════════════════════════════════════════════════════════════════
-- Décision produit : le RESTAURATEUR (et le GROUP_ADMIN) doivent pouvoir LIRE les
-- paliers de fidélité PLATEFORME (OneClick Lounge) en LECTURE SEULE, pour afficher
-- le bloc « Paliers globaux Lounge » côté ProDesk. La MODIFICATION reste réservée
-- au SUPERADMIN (on ne grante PAS CREATE/UPDATE/DELETE:LOYALTY_TIER).
--
-- Contexte : la ressource LOYALTY_TIER (migration V43) n'accordait
-- VIEW/CREATE/UPDATE/DELETE qu'au SUPERADMIN. Le RESTAURATEUR n'avait donc PAS
-- VIEW:LOYALTY_TIER → 403 sur :
--   • GET /api/loyalty/tier-rules            (liste des paliers)
--   • GET /api/loyalty/tier-rules/assignments/counts
--   • GET /api/loyalty/tier-rules/{id}/assignments
-- (les 3 GET sont gardés par @PreAuthorize("hasAuthority('VIEW:LOYALTY_TIER')")).
--
-- Périmètre du grant :
--   • RESTAURATEUR  → VIEW seulement (lecture du bloc paliers Lounge en ProDesk).
--   • GROUP_ADMIN   → VIEW seulement (un group admin gère plusieurs restos et
--                     surface le même bloc paliers ; read-only cohérent — pattern
--                     identique à V66/V94/V95 où RESTAURATEUR + GROUP_ADMIN sont
--                     accordés ensemble pour la lecture).
-- On NE grante PAS CREATE/UPDATE/DELETE → la modification des paliers reste admin.
--
-- RBAC v2 senior strict : la sécurité du contrôleur reste
-- @PreAuthorize("hasAuthority('VERB:LOYALTY_TIER')") inchangée (jamais
-- hasRole/isAuthenticated). V99 est purement additive : un SEUL grant RBAC en DB.
--
-- Idempotent (NOT EXISTS, cf V43/V66/V94/V95). created_at/updated_at/id : defaults DB.
--
-- ⚠️ Après application : un userDetails déjà en cache (TTL ~1 h) ne verra le grant
-- qu'après éviction / ré-login (le restaurateur déjà loggé doit se reconnecter).

INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'GROUP_ADMIN')
  AND m.code = 'LOYALTY_TIER'
  AND a.code = 'VIEW'
  AND NOT EXISTS (
      SELECT 1 FROM permissions p
      WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
