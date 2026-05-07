-- ════════════════════════════════════════════════════════════════════
-- Joué automatiquement par le container postgres au PREMIER démarrage
-- (quand le volume oneclick_pgdata est vide).
--
-- Ne joue PAS sur les démarrages suivants → les modifs ici n'affectent
-- pas une DB existante. Pour reset : `docker compose down -v` puis up.
-- ════════════════════════════════════════════════════════════════════

-- Le user POSTGRES_USER (hh) est créé par le container avec password
-- POSTGRES_PASSWORD. La DB POSTGRES_DB (oneclick_local) est créée et
-- ownée par hh. On ajoute juste oneclick_app pour matcher SETUP-LOCAL.md
-- + le setup BYPASSRLS qu'on appliquera après le restore du dump.

CREATE USER oneclick_app WITH PASSWORD 'OneclickLocal2026';

GRANT CONNECT ON DATABASE oneclick_local TO oneclick_app;

-- Crée le schéma auth (Supabase shim) avant le restore du dump.
-- Le pg_dump prod va recréer auth.users etc. par-dessus.
CREATE SCHEMA IF NOT EXISTS auth;
GRANT USAGE ON SCHEMA auth TO oneclick_app;

-- BYPASSRLS — nécessaire pour que oneclick_app voie les rows malgré
-- les RLS héritées du dump Supabase (cf SETUP-LOCAL.md Phase 3).
ALTER ROLE oneclick_app BYPASSRLS;

-- Les GRANT sur les tables se font après le restore (db-docker-restore.sh).
