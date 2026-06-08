-- ════════════════════════════════════════════════════════════════════════
-- V91 — Rôle programme MEMBER + pliage par membership + durcissement CLIENT
-- ════════════════════════════════════════════════════════════════════════
-- P1 de la politique « 1 compte OneClick + memberships additives ».
--
-- Modèle (hasAuthority strict) : l'ACCÈS au contenu d'un programme découle d'une
-- MEMBERSHIP, pas d'une autorité globale sur le rôle CLIENT. On crée un rôle
-- générique MEMBER portant les autorités programme, on y rattache les memberships
-- (backfill V90), et on RETIRE ces autorités du rôle CLIENT global (elles sont
-- désormais octroyées uniquement via la membership → un non-membre n'y a plus accès).
--
-- Les autorités MEMBER sont pliées dans le security context par
-- OneClickUserDetailsService (MembershipDirectoryApi.authoritiesFor). Pour les 28
-- membres palmeraie + 55 homu (backfill V90), l'accès reste identique (ils gardent
-- les autorités, désormais via MEMBER). Pour un client oneclick pur : il PERD ces
-- autorités (il n'avait de toute façon aucune donnée programme) → vrai gate hasAuthority.

-- 1. Rôle programme générique (id aligné sur la convention des rôles seedés ...0001..0005).
INSERT INTO roles (id, code, name, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000006', 'MEMBER', 'Membre programme', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 2. Copie data-driven des permissions PROGRAMME du CLIENT vers MEMBER (mêmes paires action×menu).
INSERT INTO permissions (id, role_id, menu_id, action_id, created_at)
SELECT gen_random_uuid(), '10000000-0000-0000-0000-000000000006', p.menu_id, p.action_id, now()
FROM permissions p
JOIN roles r ON r.id = p.role_id
JOIN menus m ON m.id = p.menu_id
WHERE r.code = 'CLIENT'
  AND m.code IN ('FAMILY','BOOKINGS','RESOURCE_BOOKINGS','EVENTS','EVENT_RSVP','FEEDBACK','SEMINARS')
ON CONFLICT DO NOTHING;

-- 3. Rattache les memberships (backfill V90, role_id NULL) au rôle MEMBER.
UPDATE tenant_memberships
SET role_id = '10000000-0000-0000-0000-000000000006', updated_at = now()
WHERE role_id IS NULL AND deleted_at IS NULL;

-- 4. Durcissement : retire les permissions PROGRAMME du rôle CLIENT global.
--    (Ces capacités ne concernent que les programmes whitelabel — un client OneClick de
--     base ne les utilise pas ; elles passent exclusivement par la membership.)
DELETE FROM permissions p
USING roles r, menus m
WHERE p.role_id = r.id AND p.menu_id = m.id
  AND r.code = 'CLIENT'
  AND m.code IN ('FAMILY','BOOKINGS','RESOURCE_BOOKINGS','EVENTS','EVENT_RSVP','FEEDBACK','SEMINARS');
