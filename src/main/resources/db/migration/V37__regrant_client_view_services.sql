-- ════════════════════════════════════════════════════════════════════
-- V37 — Re-grant VIEW:SERVICES au rôle CLIENT (ajuste P3/V36)
-- ════════════════════════════════════════════════════════════════════
-- V36 (P3) avait retiré au CLIENT : DELETE:SUPPORT + VIEW sur
-- STAFF/TABLES/ZONES/SERVICES. Mais le flux de RÉSERVATION client
-- (useOneClickAvailability) lit les « services repas » (brunch/déjeuner/dîner)
-- pour calculer les créneaux disponibles → sans VIEW:SERVICES, le booking
-- spamme des 403.
--
-- Les services repas sont de l'INFO DE RÉSERVATION (horaires de service), pas
-- de la config interne comme STAFF/TABLES/ZONES. On rend donc VIEW:SERVICES
-- au CLIENT. Les écritures (CREATE/UPDATE/DELETE:SERVICES) restent réservées
-- staff/admin — verrouillées côté contrôleur en P2 (RestaurantController +
-- RestaurantAccessGuard). STAFF/TABLES/ZONES + DELETE:SUPPORT restent retirés.
--
-- Idempotent (NOT EXISTS) — même pattern que V34/V35.
-- ⚠️ Après application : flusher le cache userDetails (Redis) sinon les CLIENT
-- déjà en cache ne voient pas le nouveau droit avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE r.code = 'CLIENT'
  AND m.code = 'SERVICES'
  AND a.code = 'VIEW'
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
