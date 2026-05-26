-- ════════════════════════════════════════════════════════════════════
-- V40 — Grant UPLOAD:MEDIA au rôle CLIENT
-- ════════════════════════════════════════════════════════════════════
-- Le CLIENT uploade des médias dans deux parcours Pocket cœur :
--   • avatar de profil (OneClickProfile → mediaService.uploadAvatar)
--   • photo de ticket Snap2Earn pour l'OCR (CameraTicketCapture)
-- Les deux passent par POST /api/media/upload, gardé par UPLOAD:MEDIA. Le CLIENT
-- ne détenait AUCUN droit sur le menu MEDIA → 403 silencieux sur l'avatar ET l'OCR.
--
-- Sûr : l'upload crée juste une row media + stocke le binaire (validation MIME +
-- taille max + rate-limit Bucket4j). Pas d'escalade. Même pattern idempotent que
-- V34/V35/V38.
-- ⚠️ Après application : flusher le cache userDetails (Redis) sinon les CLIENT
-- déjà en cache ne voient pas le nouveau droit avant le TTL 1 h.
-- ════════════════════════════════════════════════════════════════════

INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE r.code = 'CLIENT'
  AND m.code = 'MEDIA'
  AND a.code = 'UPLOAD'
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
