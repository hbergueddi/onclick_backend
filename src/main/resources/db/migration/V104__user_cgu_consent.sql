-- V104 — RGPD : trace du consentement CGU/Politique de confidentialité au signup.
--
-- Colonne nullable (les comptes existants n'ont pas de trace → null = legacy/non renseigné).
-- Renseignée à `now()` au register quand le client envoie `cguAccepted=true`. Champ d'audit
-- (preuve de consentement) ; pas exposé en lecture publique. L'exigence stricte côté serveur
-- (refus si non accepté) sera durcie quand tous les clients enverront le flag.
ALTER TABLE users ADD COLUMN IF NOT EXISTS cgu_accepted_at timestamptz;
