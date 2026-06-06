-- ════════════════════════════════════════════════════════════════════
-- V74 — Branding tenant complet (parité 1:1 éditeur whitelabel)
-- ════════════════════════════════════════════════════════════════════
-- La table tenant_brandings (V1) ne portait que 4 champs (logo_url, primary_color,
-- accent_color, custom_domain). L'éditeur de branding legacy en gère 8 de plus :
-- couleur de fond, logo sombre, favicon, tagline, et les noms d'app iOS (Win/Store).
-- Cette migration ajoute les 6 colonnes manquantes pour la parité 1:1 de la page
-- TenantBranding (portail tenant-admin C1, SUPERADMIN-only).
--
-- Additive pure (ADD COLUMN IF NOT EXISTS, nullable, sans default) → 0 impact sur les
-- lignes existantes ni sur les autres tenants. Pas de RBAC (réutilise VIEW/UPDATE:TENANTS).
--
-- NB : app_name_win / app_name_store sont stockés (saisis par le SUPERADMIN) mais non
-- consommés côté build iOS dans ce périmètre (on ne touche pas aux apps natives) — ils
-- existent pour la complétude de l'éditeur, exactement comme le legacy.
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE tenant_brandings ADD COLUMN IF NOT EXISTS background_color text;
ALTER TABLE tenant_brandings ADD COLUMN IF NOT EXISTS logo_dark_url    text;
ALTER TABLE tenant_brandings ADD COLUMN IF NOT EXISTS favicon_url      text;
ALTER TABLE tenant_brandings ADD COLUMN IF NOT EXISTS tagline          text;
ALTER TABLE tenant_brandings ADD COLUMN IF NOT EXISTS app_name_win     text;
ALTER TABLE tenant_brandings ADD COLUMN IF NOT EXISTS app_name_store   text;
