-- ════════════════════════════════════════════════════════════════════
-- V62 — Ressource RBAC DISPUTES (Feature #3 — contestation no-show)
-- ════════════════════════════════════════════════════════════════════
-- Ajoute la ressource DISPUTES + ses 3 autorités et les accorde aux rôles, en
-- suivant EXACTEMENT le schéma de V32/V43 (menus + permissions via cross-join
-- roles × menus × actions, idempotent NOT EXISTS).
--
-- RBAC v2 senior strict : @PreAuthorize("hasAuthority('VERB:DISPUTES')") partout
-- (jamais isAuthenticated/hasRole). Le scoping fin (ownership client, staff-of-
-- restaurant, phase d'escalade) est dans NoShowDisputeService (ABAC).
--
-- Grants :
--   • CLIENT                      → CREATE + VIEW   (conteste + suit sa contestation)
--   • RESTAURATEUR + STAFF        → VIEW + UPDATE   (dashboard resto + résout en phase resto)
--   • SUPERADMIN + GROUP_ADMIN    → VIEW + UPDATE   (support/admin : résout en phase support)
--   • SUPPORT                     → VIEW + UPDATE   (idempotent ; rôle absent du seed actuel
--                                    → simplement ignoré par le NOT EXISTS. Présent pour la
--                                    cible produit où un rôle SUPPORT dédié existerait.)
--
-- Note : aucun rôle ne reçoit DELETE:DISPUTES — une contestation ne se supprime pas
-- (audit), elle se résout (accepted/refused via UPDATE). Pas d'over-grant.
--
-- ⚠️ Après application : flusher le cache userDetails (redis-cli FLUSHDB) sinon les
-- sessions en cache n'ont pas les nouvelles autorités avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- ── 1. Ressource DISPUTES (feuille rattachée à la catégorie Réservations) ───
INSERT INTO menus (code, name, parent_id, sort_order)
SELECT 'DISPUTES', 'Contestations no-show',
       (SELECT id FROM menus WHERE code = 'RESERVATION_MGMT'), 23
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'DISPUTES');

-- ── 2. CLIENT → CREATE + VIEW ───────────────────────────────────────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code = 'CLIENT'
  AND m.code = 'DISPUTES'
  AND a.code IN ('CREATE', 'VIEW')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 3. RESTAURATEUR + STAFF → VIEW + UPDATE ─────────────────────────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'STAFF')
  AND m.code = 'DISPUTES'
  AND a.code IN ('VIEW', 'UPDATE')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 4. SUPERADMIN + GROUP_ADMIN + SUPPORT → VIEW + UPDATE ────────────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('SUPERADMIN', 'GROUP_ADMIN', 'SUPPORT')
  AND m.code = 'DISPUTES'
  AND a.code IN ('VIEW', 'UPDATE')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);
