-- V77 — E1 OnboardingWizard : marqueur de complétion du parcours self-service resto.
--
-- Le legacy (origin/4Click) suit la fin du wizard d'onboarding restaurateur via
-- restaurants.onboarding_completed_at. Colonne absente du schéma Spring → on l'ajoute.
--
-- Sémantique : NULL = wizard non terminé (afficher le wizard à la 1re connexion owner) ;
-- timestamp = onboarding terminé (timestamp serveur posé par POST /onboarding-complete).
-- Idempotent (IF NOT EXISTS) — rejouable sans effet.

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS onboarding_completed_at timestamptz;

COMMENT ON COLUMN restaurants.onboarding_completed_at IS
    'E1 — instant de complétion du wizard onboarding self-service (NULL = non terminé).';
