-- ════════════════════════════════════════════════════════════════════
-- V46 — Modération des médias : statut sur la table polymorphique `medias`
-- ════════════════════════════════════════════════════════════════════
-- La page admin GALAXY ModerationMedias lisait/écrivait restaurant_media via le
-- shim supabase.from("restaurant_media") — table consolidée dans `medias` (V2,
-- polymorphique). Le modèle `medias` ne portait AUCUN statut de modération →
-- lecture [] / écriture no-op silencieuse.
--
-- On AJOUTE une colonne moderation_status à `medias` (additif, non destructif).
-- Les médias existants → 'approved' (pas de masquage rétroactif). Workflow :
-- pending → approved | rejected.
--
-- RBAC v2 senior strict : ressource DÉDIÉE MEDIA_MODERATION (VIEW + UPDATE)
-- accordée à SUPERADMIN — distincte de MEDIA (dont le CLIENT détient UPLOAD pour
-- son avatar / l'OCR, cf V40). La modération ne doit PAS hériter de ces droits.
-- @PreAuthorize = hasAuthority('VERB:MEDIA_MODERATION'), jamais isAuthenticated()/hasRole().
--
-- Idempotent. ⚠️ Après application : redis-cli FLUSHDB (nouvelle autorité).
-- ════════════════════════════════════════════════════════════════════

-- 1) Colonne moderation_status (additive, défaut 'approved' sur l'existant) ────
ALTER TABLE medias ADD COLUMN IF NOT EXISTS moderation_status TEXT NOT NULL DEFAULT 'approved';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'medias_moderation_status_check') THEN
        ALTER TABLE medias ADD CONSTRAINT medias_moderation_status_check
            CHECK (moderation_status IN ('pending', 'approved', 'rejected'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_medias_moderation
    ON medias(moderation_status) WHERE deleted_at IS NULL;

-- 2) Ressource RBAC MEDIA_MODERATION (menu non-sidebar) ───────────────────────
INSERT INTO menus (id, code, name, icon, sort_order)
SELECT gen_random_uuid(), 'MEDIA_MODERATION', 'Modération médias', 'image-check', 905
WHERE NOT EXISTS (SELECT 1 FROM menus WHERE code = 'MEDIA_MODERATION');

-- 3) Grant VIEW + UPDATE:MEDIA_MODERATION → SUPERADMIN uniquement ──────────────
INSERT INTO permissions (role_id, menu_id, action_id)
SELECT r.id, m.id, a.id
FROM roles r
CROSS JOIN menus m
CROSS JOIN actions a
WHERE r.code = 'SUPERADMIN'
  AND m.code = 'MEDIA_MODERATION'
  AND a.code IN ('VIEW', 'UPDATE')
  AND NOT EXISTS (
    SELECT 1 FROM permissions p
    WHERE p.role_id = r.id AND p.menu_id = m.id AND p.action_id = a.id
  );
