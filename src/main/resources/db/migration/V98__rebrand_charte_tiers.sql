-- V98 — Rebrand des paliers de fidélité vers la charte graphique (rebranding iOS/charte).
--
-- Contexte : la table `tiers` portait un seed CASSÉ inséré HORS Flyway (Ruby/Sapphire/Émeraude/Black
-- répliqués par tenant, TOUS avec min_points = 1234 → « 1 234 PTS » identique sur tous les paliers).
-- La charte verrouille 5 paliers sommeliers (Connaisseur → Grand Cru → Signature → Ambassadeur →
-- Table Secrète) avec les seuils maquette (200 / 500 / 1 000 / 10 000 / 50 000), validés client.
--
-- Mapping (préserve le rang du membre : Black = sommet → Table Secrète ; Ambassadeur = net-new) :
--   Ruby     → Connaisseur    (200,   ordre 1)
--   Sapphire → Grand Cru      (500,   ordre 2)
--   Émeraude → Signature      (1 000, ordre 3)
--   (net-new)  Ambassadeur    (10 000, ordre 4)
--   Black    → Table Secrète  (50 000, ordre 5)
--
-- Idempotente : les UPDATE filtrent sur l'ANCIEN nom (no-op au 2e passage) ; l'INSERT Ambassadeur
-- est protégé par ON CONFLICT (tenant_id, name). N'agit que sur les tenants déjà seedés ; les tenants
-- sans paliers continuent de s'appuyer sur le fallback canonique de LoyaltyTierResolver (réaligné).

UPDATE tiers SET name = 'Connaisseur',   min_points = 200,   sort_order = 1, updated_at = now()
  WHERE name = 'Ruby';
UPDATE tiers SET name = 'Grand Cru',     min_points = 500,   sort_order = 2, updated_at = now()
  WHERE name = 'Sapphire';
UPDATE tiers SET name = 'Signature',     min_points = 1000,  sort_order = 3, updated_at = now()
  WHERE name IN ('Émeraude', 'Emeraude');
UPDATE tiers SET name = 'Table Secrète', min_points = 50000, sort_order = 5, updated_at = now()
  WHERE name IN ('Black', 'Noir');

-- Ambassadeur : 5e palier net-new, inséré pour chaque tenant déjà doté de paliers (via les lignes
-- Table Secrète issues du renommage), bonus_percent hérité du tenant.
INSERT INTO tiers (id, tenant_id, name, min_points, bonus_percent, sort_order)
SELECT gen_random_uuid(), t.tenant_id, 'Ambassadeur', 10000, t.bonus_percent, 4
FROM tiers t
WHERE t.name = 'Table Secrète'
ON CONFLICT (tenant_id, name) DO UPDATE
  SET min_points = EXCLUDED.min_points, sort_order = EXCLUDED.sort_order, updated_at = now();

COMMENT ON TABLE tiers IS 'Niveaux de fidélité charte (Connaisseur, Grand Cru, Signature, Ambassadeur, Table Secrète) avec bonus_percent et seuils min_points';
