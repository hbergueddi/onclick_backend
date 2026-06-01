-- ════════════════════════════════════════════════════════════════════
-- V60 — index trigramme (pg_trgm GIN) pour la recherche client (B3)
-- ════════════════════════════════════════════════════════════════════
--
-- Deux requêtes font du `LOWER(col) LIKE '%q%'` (wildcard en tête → un btree
-- standard est inutilisable, seq-scan sur ~17 600 users) :
--   - UserRepository.searchClients  (GET /api/users/clients/search, #5)
--       OR sur lower(first_name) / lower(last_name) / lower(coalesce(phone,''))
--   - AdminViewsService.findAdminUsers (GET /api/analytics/admin-users)
--       OR sur lower(first_name) / lower(last_name) / lower(email)
--
-- pg_trgm + index GIN sur l'EXPRESSION exacte de chaque branche → le planner
-- résout chaque `LIKE '%q%'` (q ≥ 3 car. = 1 trigramme) par bitmap-index-scan
-- au lieu d'un seq-scan. (q < 3 car. retombe sur seq-scan — cas marginal, le
-- endpoint impose q ≥ 2 mais les recherches réelles font ≥ 3 car.)
--
-- Table à 17 600 lignes → build d'index quasi instantané, CREATE INDEX standard
-- (transactionnel Flyway) suffit ; pas besoin de CONCURRENTLY à cette échelle.
-- Idempotent (IF NOT EXISTS) pour les DB déjà restaurées.
-- ════════════════════════════════════════════════════════════════════

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Expressions alignées sur searchClients + findAdminUsers (lower(...)).
CREATE INDEX IF NOT EXISTS idx_users_first_name_trgm
    ON users USING gin (lower(first_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_last_name_trgm
    ON users USING gin (lower(last_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_email_trgm
    ON users USING gin (lower(email) gin_trgm_ops);

-- phone est NULLABLE et searchClients indexe LOWER(COALESCE(phone, '')) →
-- index sur la même expression pour qu'il soit utilisable par cette branche.
CREATE INDEX IF NOT EXISTS idx_users_phone_trgm
    ON users USING gin (lower(coalesce(phone, '')) gin_trgm_ops);
