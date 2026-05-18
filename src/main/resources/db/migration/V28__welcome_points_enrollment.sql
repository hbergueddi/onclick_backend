-- ════════════════════════════════════════════════════════════════════
-- V28 — Welcome points enrollment (port commit legacy e7a8b49b)
-- ════════════════════════════════════════════════════════════════════
--
-- Feature "Inscrire membre" : le restaurateur enrôle un nouveau client
-- dans le programme fidélité avec un bonus de bienvenue en points.
--
-- Le bonus par défaut + le plafond sont configurés par owner/admin.
-- Le staff ne peut pas dépasser le plafond (anti-abus côté service).
--
-- LEGACY → SPRING differences :
--   - Table : legacy `restaurant_gain_rules` → Spring `gain_rules` (rename V13)
--   - RPC SECURITY DEFINER `enroll_member` → Spring EnrollmentService Java
--     (cross-module via API publiques identity.UserService +
--     restaurant.RestaurantStaffService + propre loyalty)
--   - reason='welcome' insert dans loyalty_points → identique
--   - listEnrollments → query Spring via /api/loyalty/enrollments/...
--
-- Cette migration ajoute UNIQUEMENT les colonnes — toute la logique
-- métier est dans EnrollmentService (cf Modulith CLOSED). Pas de RPC.

BEGIN;

-- ─── Colonnes welcome points sur gain_rules ───
ALTER TABLE gain_rules
  ADD COLUMN IF NOT EXISTS welcome_points_default INT NOT NULL DEFAULT 100,
  ADD COLUMN IF NOT EXISTS welcome_points_max     INT NOT NULL DEFAULT 500;

COMMENT ON COLUMN gain_rules.welcome_points_default IS
  'Bonus de bienvenue par défaut crédité à l''inscription d''un nouveau membre.
   Valeur pré-remplie dans le formulaire EnrollMember (front).';
COMMENT ON COLUMN gain_rules.welcome_points_max IS
  'Plafond absolu du bonus de bienvenue. EnrollmentService rejette toute
   demande > welcome_points_max (anti-abus staff). 0 = inscription possible
   sans points, max = valeur arbitraire éditoriale.';

-- ─── Contraintes de cohérence ───
ALTER TABLE gain_rules
  DROP CONSTRAINT IF EXISTS gain_rules_welcome_default_positive,
  ADD  CONSTRAINT gain_rules_welcome_default_positive
       CHECK (welcome_points_default >= 0),
  DROP CONSTRAINT IF EXISTS gain_rules_welcome_max_gte_default,
  ADD  CONSTRAINT gain_rules_welcome_max_gte_default
       CHECK (welcome_points_max >= welcome_points_default);

COMMIT;
