-- ════════════════════════════════════════════════════════════════════
-- V35 — CREATE:LOYALTY pour STAFF (Snap2Earn / scan ticket)
-- ════════════════════════════════════════════════════════════════════
-- Décision produit : le STAFF (« Contrôleur ») scanne les tickets clients
-- (Snap2Earn / earn / ocr-receipt) et enregistre les ratings honoré/no_show.
-- Or POST /api/loyalty/snap2earn|earn|ocr-receipt|ratings est gardé par
-- hasAuthority('CREATE:LOYALTY'), authority que STAFF n'avait pas → 403 sur
-- le cœur de son métier (même classe de bug que NOTIFICATIONS, cf V34).
--
-- On accorde CREATE:LOYALTY à STAFF. Les transitions de réservation
-- (confirmer / annuler / contre-proposer / honoré / no_show) passent déjà par
-- PATCH /api/reservations/{id}/status = UPDATE:RESERVATIONS, déjà détenu par STAFF.
--
-- ⚠️ Garde-fou : CREATE:LOYALTY est une authority GROSSIÈRE qui couvre aussi
-- createGainRule / createGainRuleRequest / createRestitution (config restaurant
-- + restitutions financières) — fonctions GÉRANT/admin, PAS staff. Ces 3
-- endpoints sont refermés au niveau contrôleur via
-- SecurityHelper.requireManagerOrAdmin() (RESTAURATEUR ou admin uniquement) :
-- la montée de privilège du grant grossier est donc neutralisée côté applicatif.
--
-- Idempotent (NOT EXISTS) — même pattern que V33/V34.
-- ════════════════════════════════════════════════════════════════════

INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE r.code = 'STAFF'
  AND m.code = 'LOYALTY'
  AND a.code = 'CREATE'
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
