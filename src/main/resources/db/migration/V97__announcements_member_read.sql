-- ════════════════════════════════════════════════════════════════════
-- V97 — « Annonces » : lecture MEMBRE (les membres d'un programme lisent
--        les annonces PUBLIÉES de leur tenant programme)
-- ════════════════════════════════════════════════════════════════════
-- Décision produit (11/06/26) : la tuile membre « Annonces du Club » (PccHome
-- → GET /api/announcements) doit afficher au MEMBRE (CLIENT avec membership
-- programme active : PCC/HOMU/futurs) les annonces publiées vivantes de SON
-- tenant programme — pas seulement au staff B2B.
--
-- V70 réservait VIEW:ANNOUNCEMENTS au staff (STAFF/RESTAURATEUR/GROUP_ADMIN/
-- SUPERADMIN, « PAS CLIENT — comm interne B2B »), si bien qu'un membre PCC
-- recevait 403 au mount de la tuile. On accorde VIEW:ANNOUNCEMENTS au rôle
-- CLIENT — lecture + acquittement (mark-read) UNIQUEMENT. CREATE/UPDATE/DELETE
-- restent réservés à RESTAURATEUR/GROUP_ADMIN/SUPERADMIN (les tenant-admins qui
-- publient) — inchangés.
--
-- Le périmètre fin reste porté par l'ABAC service (jamais par le rôle seul) :
--   • AnnouncementService.listForMe : un caller membre actif de SON tenant
--     programme (MembershipDirectoryApi.isActiveMember) → findActiveForTenant
--     (annonces PUBLIÉES VIVANTES de ce tenant, ni programmées ni archivées) ;
--     un CLIENT SANS membership → liste vide (aucune fuite des annonces B2B du
--     tenant home oneclick). Les tenant-admins/staff conservent leur vue.
--   • AnnouncementService.canReadInTenant (mark-read) : + membre actif du tenant.
-- RBAC v2 senior strict : @PreAuthorize("hasAuthority('VIEW:ANNOUNCEMENTS')")
-- inchangé côté controller ; seul le grant CLIENT est ajouté ici.
--
-- Anti-régression : grant additif idempotent (NOT EXISTS) ; aucune autre table
-- ni colonne touchée ; 0 impact CREATE/UPDATE/DELETE.
--
-- ⚠️ Après application : flusher le cache userDetails (redis-cli FLUSHDB) sinon
-- les sessions CLIENT en cache n'ont pas la nouvelle autorité avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

-- VIEW:ANNOUNCEMENTS → CLIENT (lecture + mark-read ; l'ABAC service borne au
-- tenant programme du membre — annonces publiées vivantes seulement).
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code = 'CLIENT'
  AND m.code = 'ANNOUNCEMENTS'
  AND a.code = 'VIEW'
  AND NOT EXISTS (SELECT 1 FROM permissions p WHERE p.role_id=r.id AND p.menu_id=m.id AND p.action_id=a.id);
