-- ════════════════════════════════════════════════════════════════════
-- V66 — Ressource RBAC BOOKINGS (PCC Lot 0 — split d'autorité « membre »)
-- ════════════════════════════════════════════════════════════════════
-- Ajoute la ressource BOOKINGS + ses autorités et les accorde aux rôles, en
-- suivant EXACTEMENT le schéma de V32/V62 (menus + permissions via cross-join
-- roles × menus × actions, idempotent NOT EXISTS).
--
-- POURQUOI : avant V66, TOUTES les opérations du module resource_booking
-- (gestion du PARC : POST/DELETE /resources, POST /pricings = admin/staff) ET
-- les opérations MEMBRE (POST/PATCH/DELETE /bookings, POST /guests, lectures
-- bookings) partageaient la MÊME autorité {VERB}:RESOURCE_BOOKINGS. Donner à
-- CLIENT le droit de réserver (CREATE) lui aurait AUSSI donné le droit de créer
-- des ressources → escalade de privilège. On sépare donc :
--   • RESOURCE_BOOKINGS  = gestion du PARC (ressources + tarifs) — inchangé
--   • BOOKINGS           = workflow RÉSERVATION (bookings + invités) — NOUVEAU
--
-- RBAC v2 senior strict : @PreAuthorize("hasAuthority('VERB:BOOKINGS')") partout
-- (jamais isAuthenticated/hasRole). Le scoping fin (ownership organisateur du
-- booking, règle d'annulation H-2) est dans ResourceBookingService (ABAC).
--
-- Grants :
--   • CLIENT                                  → VIEW + CREATE + UPDATE + DELETE
--       (le membre réserve, modifie/annule SES bookings, ajoute SES invités ;
--        le self-scope organizer_id est forcé côté service)
--   • STAFF + RESTAURATEUR + GROUP_ADMIN
--     + SUPERADMIN                            → VIEW + CREATE + UPDATE + DELETE
--       (le staff/admin confirme, annule, marque honoree/no_show les bookings
--        des membres ; pas de filtre self-scope pour eux)
--
-- NB : CLIENT conserve par ailleurs VIEW:RESOURCE_BOOKINGS (seedé en V32) pour
-- LISTER les ressources réservables (GET /resources reste VIEW:RESOURCE_BOOKINGS).
-- On ne RETIRE rien : V66 est purement additive.
--
-- ⚠️ Après application : flusher le cache userDetails (redis-cli FLUSHDB) sinon les
-- sessions en cache n'ont pas les nouvelles autorités avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- ── 1. Ressource BOOKINGS (feuille rattachée à la catégorie Réservations) ───
-- Parent RESERVATION_MGMT (comme RESERVATIONS=21, RESOURCE_BOOKINGS=22,
-- DISPUTES=23) — BOOKINGS prend sort_order 24.
INSERT INTO menus (code, name, parent_id, sort_order)
SELECT 'BOOKINGS', 'Réservations ressources (membre)',
       (SELECT id FROM menus WHERE code = 'RESERVATION_MGMT'), 24
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'BOOKINGS');

-- ── 2. CLIENT → VIEW + CREATE + UPDATE + DELETE ─────────────────────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code = 'CLIENT'
  AND m.code = 'BOOKINGS'
  AND a.code IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);

-- ── 3. STAFF + RESTAURATEUR + GROUP_ADMIN + SUPERADMIN → VIEW+CREATE+UPDATE+DELETE ──
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('STAFF', 'RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
  AND m.code = 'BOOKINGS'
  AND a.code IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE')
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);
