-- ════════════════════════════════════════════════════════════════════
-- V42 — Ressource RBAC « EVENT_RSVP » : self-service RSVP/annulation events
-- ════════════════════════════════════════════════════════════════════
-- RBAC v2 senior strict : @PreAuthorize = hasAuthority('VERB:RESOURCE'), jamais
-- isAuthenticated()/hasRole(). Le RSVP Pocket (POST /api/events/participations)
-- et son annulation (DELETE /api/events/participations/by-event/{e}/user/{u})
-- étaient protégés par UPDATE:EVENTS — or UPDATE:EVENTS est une autorité ADMIN
-- qui protège aussi PATCH /api/events/{id} (édition d'événement). Donner
-- UPDATE:EVENTS au CLIENT serait une escalade (édition arbitraire d'events).
--
-- S'inscrire à un événement est une action self-service distincte de la gestion
-- d'événements → ressource dédiée EVENT_RSVP (mirror de PROFILE V41 / COMMUNITY V38) :
--   • CREATE:EVENT_RSVP → s'inscrire (POST /participations)
--   • DELETE:EVENT_RSVP → annuler son inscription (DELETE /participations/...)
-- accordée à TOUS les rôles (tout user peut s'inscrire). Le scoping « soi-même »
-- est garanti côté contrôleur par SecurityHelper.requireOwnerOrAdmin(userId)
-- (ABAC) — corrige au passage un IDOR latent (le userId venait du body/path sans
-- vérification d'appartenance). La lecture (GET .../participations/by-user/{u})
-- reste sur VIEW:EVENTS (déjà owner-checkée, CLIENT l'a).
--
-- Idempotent (NOT EXISTS). ⚠️ Après application : flusher le cache userDetails
-- (Redis) sinon les sessions déjà en cache n'ont pas CREATE/DELETE:EVENT_RSVP
-- → RSVP renverrait 403 jusqu'au TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- 1) Menu EVENT_RSVP (ressource self-service ; pas une entrée de sidebar → path/parent NULL).
INSERT INTO menus (id, code, name, icon, sort_order)
SELECT gen_random_uuid(), 'EVENT_RSVP', 'Mes inscriptions événements', 'calendar-check', 901
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'EVENT_RSVP');

-- 2) Grant CREATE:EVENT_RSVP + DELETE:EVENT_RSVP à TOUS les rôles.
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE m.code = 'EVENT_RSVP'
  AND a.code IN ('CREATE', 'DELETE')
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
