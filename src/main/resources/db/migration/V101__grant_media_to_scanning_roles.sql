-- ════════════════════════════════════════════════════════════════════════
-- V101 — Grant MEDIA (UPLOAD/CREATE/VIEW) aux rôles qui scannent (Snap2Earn)
-- ════════════════════════════════════════════════════════════════════════
-- BUG : un owner/staff qui scanne un ticket AVEC photo échoue sur l'upload
--   POST /api/media/upload  (hasAuthority('UPLOAD:MEDIA'))
-- → toast « accès refusé », alors que le crédit de points (POST /api/loyalty/
-- snap2earn, CREATE:LOYALTY) réussit (appels distincts). Les rôles porteurs de
-- CREATE:LOYALTY (= ceux qui scannent : RESTAURATEUR, STAFF, GROUP_ADMIN)
-- n'avaient AUCUNE permission MEDIA (seuls CLIENT[UPLOAD] et SUPERADMIN[*] en
-- avaient). L'OCR (ocr-receipt) a aussi besoin de l'image uploadée → le flux
-- photo+OCR était cassé pour eux (saisie manuelle seule possible).
--
-- FIX : accorder UPLOAD + CREATE + VIEW sur la ressource MEDIA à ces 3 rôles.
--   - UPLOAD : POST /api/media/upload (photo du ticket — la cause du 403).
--   - CREATE : POST /api/media et /api/media/files (enregistrement média, ex avatar staff).
--   - VIEW   : GET /api/media (relecture des médias par le staff).
--   - DELETE : volontairement EXCLU (moindre privilège ; suppression = admin).
--
-- Aucun nouvel endpoint, aucune nouvelle règle de sécurité : on octroie des
-- AUTORITÉS EXISTANTES (les @PreAuthorize média sont déjà en hasAuthority(...)).
-- Pattern idempotent NOT EXISTS (cf V94 / V99 / V62).

INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r CROSS JOIN menus m CROSS JOIN actions a
WHERE r.code IN ('RESTAURATEUR', 'STAFF', 'GROUP_ADMIN')
  AND m.code = 'MEDIA'
  AND a.code IN ('UPLOAD', 'CREATE', 'VIEW')
  AND NOT EXISTS (
      SELECT 1 FROM permissions p
      WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
