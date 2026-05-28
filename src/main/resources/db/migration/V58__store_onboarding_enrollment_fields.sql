-- ════════════════════════════════════════════════════════════════════
-- V58 — store_onboarding_requests : champs enrollment legacy
-- ════════════════════════════════════════════════════════════════════
--
-- Le formulaire public d'inscription Store (StoreOnboarding.tsx, 3 étapes)
-- collecte des champs fiscaux / opérationnels que la table V22 ne portait
-- PAS : budget, description, fonction du responsable, ICE / IF / RC / patente,
-- capacité (couverts) et services proposés. Ils étaient donc silencieusement
-- perdus à la soumission et jamais visibles dans la fiche admin
-- (/forge/demandes-inscription/{id}).
--
-- Cette migration restaure la parité avec le schéma Supabase legacy
-- (onboarding_requests) afin que l'admin dispose des infos nécessaires à la
-- décision approve/reject.
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE store_onboarding_requests ADD COLUMN IF NOT EXISTS budget      VARCHAR(8);
ALTER TABLE store_onboarding_requests ADD COLUMN IF NOT EXISTS description VARCHAR(2000);
ALTER TABLE store_onboarding_requests ADD COLUMN IF NOT EXISTS owner_role  VARCHAR(64);
ALTER TABLE store_onboarding_requests ADD COLUMN IF NOT EXISTS ice         VARCHAR(32);
ALTER TABLE store_onboarding_requests ADD COLUMN IF NOT EXISTS if_number   VARCHAR(32);
ALTER TABLE store_onboarding_requests ADD COLUMN IF NOT EXISTS rc          VARCHAR(64);
ALTER TABLE store_onboarding_requests ADD COLUMN IF NOT EXISTS patente     VARCHAR(64);
ALTER TABLE store_onboarding_requests ADD COLUMN IF NOT EXISTS capacity    INTEGER;
ALTER TABLE store_onboarding_requests ADD COLUMN IF NOT EXISTS services    TEXT[];
