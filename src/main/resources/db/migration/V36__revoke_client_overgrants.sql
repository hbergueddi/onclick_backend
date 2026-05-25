-- ════════════════════════════════════════════════════════════════════
-- V36 — Retrait des sur-droits du rôle CLIENT (P3)
-- ════════════════════════════════════════════════════════════════════
-- Décision produit : un CLIENT ne doit PAS pouvoir supprimer un ticket support,
-- ni VOIR le staff / les tables / les zones / les créneaux service d'un restaurant
-- (données opérationnelles ProDesk, pas destinées au client).
--
-- On révoque pour CLIENT :
--   • DELETE:SUPPORT   (aucun endpoint ne l'exposait, mais l'authority traînait)
--   • VIEW:STAFF, VIEW:TABLES, VIEW:ZONES, VIEW:SERVICES
--
-- Complète le sweep owner-check P2 : pour SERVICES/TABLES/ZONES, les écritures
-- étaient déjà gardées staff/admin (V35-batch3b) ; ici on ferme aussi la LECTURE
-- CLIENT au niveau RBAC (@PreAuthorize → 403). Pour STAFF, listStaff était déjà
-- gardé requireAdminOrActiveStaffOf (batch3a) ; on retire en plus l'authority.
--
-- DELETE naturellement idempotent (re-run = 0 ligne supprimée).
-- ════════════════════════════════════════════════════════════════════

DELETE FROM permissions p
USING roles r, menus m, actions a
WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  AND r.code = 'CLIENT'
  AND (
        (m.code = 'SUPPORT'  AND a.code = 'DELETE')
     OR (m.code = 'STAFF'    AND a.code = 'VIEW')
     OR (m.code = 'TABLES'   AND a.code = 'VIEW')
     OR (m.code = 'ZONES'    AND a.code = 'VIEW')
     OR (m.code = 'SERVICES' AND a.code = 'VIEW')
  );
