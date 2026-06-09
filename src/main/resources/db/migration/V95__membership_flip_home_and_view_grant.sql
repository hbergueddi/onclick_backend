-- ════════════════════════════════════════════════════════════════════════
-- V95 — Flip identité « un seul compte OneClick » + grant VIEW:MEMBERSHIPS (P3)
-- ════════════════════════════════════════════════════════════════════════
-- (1) FLIP : les comptes CLIENT rattachés à un tenant de programme (palmeraie/homu/futurs)
--     migrent vers le tenant home `oneclick`. Leur accès au programme reste porté par leur
--     `tenant_membership` (créée au backfill V90) — modèle additif. Après le flip, ils se
--     connectent via OneClick Win (filtre login tenant=oneclick) et leurs espaces programme
--     sont révélés par /api/me/memberships (P3), plus par users.tenant_id.
--
--     Générique (aucun slug de programme hardcodé) : on flippe TOUT CLIENT non-oneclick qui a
--     une membership ACTIVE (garde-fou : un client sans membership n'est pas touché → ne perd
--     pas son rattachement). Idempotent (2ᵉ run : plus aucune ligne ne matche).
--     SQL natif → contourne le mapping JPA users.tenant_id (insertable/updatable=false).
--
-- (2) GRANT VIEW:MEMBERSHIPS aux rôles admin de tenant (liste des membres, endpoint P3
--     GET /api/tenants/{tenantId}/members + futur dashboard). Pattern idempotent NOT EXISTS.

-- (1) FLIP des comptes clients de programme → home oneclick.
UPDATE users u
   SET tenant_id = (SELECT id FROM tenants WHERE slug = 'oneclick')
 WHERE u.role_id = (SELECT id FROM roles WHERE code = 'CLIENT')
   AND u.deleted_at IS NULL
   AND u.tenant_id IS NOT NULL
   AND u.tenant_id <> (SELECT id FROM tenants WHERE slug = 'oneclick')
   AND EXISTS (
       SELECT 1 FROM tenant_memberships tm
        WHERE tm.user_id = u.id AND tm.status = 'active' AND tm.deleted_at IS NULL
   );

-- (2) GRANT VIEW:MEMBERSHIPS aux rôles admin de tenant.
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'MEMBERSHIPS'
  AND a.code = 'VIEW'
  AND NOT EXISTS (
      SELECT 1 FROM permissions p
      WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
