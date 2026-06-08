-- ════════════════════════════════════════════════════════════════════════
-- V92 — Corrective de V91 : restaure les permissions PROGRAMME du rôle CLIENT
-- ════════════════════════════════════════════════════════════════════════
-- V91 retirait du rôle CLIENT global les autorités programme (durcissement « non-membre →
-- 403 »). Mais certaines capacités sont volontairement ouvertes à TOUS les clients (DÉCOUVERTE :
-- lister les ressources / événements), comme l'encodent les tests d'intégration existants
-- (ex. ResourceBookingRbac « client garde VIEW:RESOURCE_BOOKINGS »).
--
-- Le durcissement correct est SURGICAL (quelles autorités deviennent membre-only vs restent
-- découverte) — une décision produit traitée séparément (P1.5). On REVIENT donc à un P1 strictement
-- ADDITIF / ISO : le rôle MEMBER (V91) + le pliage par membership restent en place ; les clients
-- conservent leurs autorités actuelles.
--
-- On ne ré-édite pas V91 (déjà appliquée) — règle de traçabilité Flyway : migration corrective.
-- Re-grant idempotent des paires (menu, action) que MEMBER possède (= exactement celles que V91
-- avait copiées du CLIENT puis retirées).
INSERT INTO permissions (id, role_id, menu_id, action_id, created_at)
SELECT gen_random_uuid(), client.id, mp.menu_id, mp.action_id, now()
FROM roles client
CROSS JOIN (
    SELECT DISTINCT p.menu_id, p.action_id
    FROM permissions p
    JOIN roles r ON r.id = p.role_id
    WHERE r.code = 'MEMBER'
) mp
WHERE client.code = 'CLIENT'
  AND NOT EXISTS (
      SELECT 1 FROM permissions x
      WHERE x.role_id = client.id AND x.menu_id = mp.menu_id AND x.action_id = mp.action_id
  );
