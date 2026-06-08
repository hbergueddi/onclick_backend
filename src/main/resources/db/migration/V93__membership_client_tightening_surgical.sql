-- ════════════════════════════════════════════════════════════════════════
-- V93 — Durcissement CHIRURGICAL du rôle CLIENT (P1.5)
-- ════════════════════════════════════════════════════════════════════════
-- Le vrai gate « non-membre → 403 » : on retire du rôle CLIENT global UNIQUEMENT les
-- autorités réellement MEMBRE-ONLY (agir dans un programme), en GARDANT les autorités de
-- DÉCOUVERTE (parcourir l'offre) ouvertes à tous les clients.
--
-- Découverte CONSERVÉE sur CLIENT (parcourir, sans membership) :
--   VIEW:RESOURCE_BOOKINGS (parc réservable), VIEW:EVENTS (agenda), VIEW:SEMINARS (séminaires).
-- Membre-only RETIRÉ du CLIENT (octroyé désormais via la membership → rôle MEMBER, plié P1) :
--   BOOKINGS (VIEW/CREATE/UPDATE/DELETE — mes réservations, busy-slots), FAMILY (toutes),
--   FEEDBACK (toutes), EVENT_RSVP (CREATE/DELETE), SEMINARS:CREATE.
--
-- MEMBER détient toujours TOUT (copié en V91) → un membre garde l'accès via le pliage.
-- Réversible (re-grant possible). Cache userDetails à flusher après application (evictAll).

-- Menus 100% membre-only : on retire toutes les actions du CLIENT.
DELETE FROM permissions p
USING roles r, menus m
WHERE p.role_id = r.id AND p.menu_id = m.id
  AND r.code = 'CLIENT'
  AND m.code IN ('FAMILY','BOOKINGS','FEEDBACK','EVENT_RSVP');

-- SEMINARS : on garde VIEW (découverte), on retire le reste (ex. CREATE = inscription membre).
DELETE FROM permissions p
USING roles r, menus m, actions a
WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  AND r.code = 'CLIENT'
  AND m.code = 'SEMINARS' AND a.code <> 'VIEW';

-- EVENTS / RESOURCE_BOOKINGS : le CLIENT n'a que VIEW (découverte) → rien à retirer (conservé).
