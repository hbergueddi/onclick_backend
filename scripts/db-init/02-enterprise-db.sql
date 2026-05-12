-- ════════════════════════════════════════════════════════════════════
-- Joué automatiquement par le container postgres au PREMIER démarrage
-- (après 01-app-user.sql, ordre lexicographique).
--
-- Crée la DB `oneclick_enterprise` utilisée par le MONOLITHE (profil
-- `enterprise`, port 8083). Schéma greenfield géré par Flyway V1+.
--
-- Cette DB est DISTINCTE de `oneclick_local` (legacy ETL source, gardée
-- intacte pour la Phase 13 — migration data legacy → enterprise).
-- ════════════════════════════════════════════════════════════════════

CREATE DATABASE oneclick_enterprise
  WITH OWNER     = oneclick_app
       ENCODING  = 'UTF8'
       LC_COLLATE = 'en_US.utf8'
       LC_CTYPE   = 'en_US.utf8'
       TEMPLATE   = template0;

GRANT CONNECT ON DATABASE oneclick_enterprise TO oneclick_app;

-- Privilèges schéma public (Postgres 17 : public n'est plus accessible
-- par défaut aux non-owners → on grant explicitement à oneclick_app).
\connect oneclick_enterprise
GRANT ALL ON SCHEMA public TO oneclick_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES    TO oneclick_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO oneclick_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO oneclick_app;
