-- BE-2 (plan RESTAURANT-ONBOARDING) — traçabilité du provisioning à l'approbation.
--
-- À l'approbation d'une demande d'inscription, le backend provisionne un restaurant + un compte
-- gérant (rôle RESTAURATEUR, mot de passe temporaire, password_must_change=true). On garde le lien
-- vers les entités créées sur la demande elle-même : (1) audit/traçabilité, (2) ancre d'idempotence
-- (re-approuver une demande déjà approuvée = no-op, on ne recrée pas le compte/resto).
ALTER TABLE store_onboarding_requests
    ADD COLUMN IF NOT EXISTS provisioned_user_id       uuid,
    ADD COLUMN IF NOT EXISTS provisioned_restaurant_id uuid;
