-- ════════════════════════════════════════════════════════════════════
-- V54 — Élargissement de `contracts` aux champs du contrat partenaire legacy
-- ════════════════════════════════════════════════════════════════════
-- La page admin /galaxy/contrats (ParametresContractuels) gère un contrat
-- riche (entité juridique, signataire, taux de commission OneClick, clauses
-- d'engagement/résiliation, aperçu PDF). Le ContractDto Spring ne portait que
-- 8 colonnes — on ajoute les champs legacy de partner_contracts pour permettre
-- la migration complète du shim supabase vers /api/financial/contracts.
--
-- Toutes les colonnes sont NULLABLE (les contrats existants restent valides) et
-- typées varchar(N) (cohérent avec ddl-auto=validate + convention V30). Idempotent.
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE contracts
    -- Signataire / représentation
    ADD COLUMN IF NOT EXISTS represented_by            varchar(128),
    ADD COLUMN IF NOT EXISTS represented_title         varchar(64),
    -- Taux & conditions financières
    ADD COLUMN IF NOT EXISTS oneclick_commission_rate  numeric(5, 2),
    ADD COLUMN IF NOT EXISTS payment_terms             varchar(256),
    ADD COLUMN IF NOT EXISTS plafond_commission_mensuel numeric(12, 2),
    -- Cycle de vie
    ADD COLUMN IF NOT EXISTS auto_renew                boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS signed_at                 timestamptz,
    ADD COLUMN IF NOT EXISTS renewal_number            integer NOT NULL DEFAULT 0,
    -- Identité juridique de l'établissement
    ADD COLUMN IF NOT EXISTS raison_sociale            varchar(128),
    ADD COLUMN IF NOT EXISTS forme_juridique           varchar(64),
    ADD COLUMN IF NOT EXISTS numero_rc                 varchar(64),
    ADD COLUMN IF NOT EXISTS numero_if                 varchar(64),
    ADD COLUMN IF NOT EXISTS numero_ice                varchar(64),
    ADD COLUMN IF NOT EXISTS capital_social            varchar(64),
    ADD COLUMN IF NOT EXISTS banque                    varchar(128),
    ADD COLUMN IF NOT EXISTS rib                       varchar(64),
    -- Exploitation
    ADD COLUMN IF NOT EXISTS capacite_couverts         integer,
    ADD COLUMN IF NOT EXISTS horaires_exploitation     varchar(256),
    ADD COLUMN IF NOT EXISTS jours_fermeture           varchar(256),
    -- Engagement / résiliation
    ADD COLUMN IF NOT EXISTS duree_engagement_mois     integer,
    ADD COLUMN IF NOT EXISTS preavis_resiliation_mois  integer,
    ADD COLUMN IF NOT EXISTS penalite_resiliation      numeric(12, 2),
    -- Signature
    ADD COLUMN IF NOT EXISTS lieu_signature            varchar(128),
    ADD COLUMN IF NOT EXISTS nombre_exemplaires        integer;
