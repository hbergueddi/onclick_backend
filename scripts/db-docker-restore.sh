#!/usr/bin/env bash
# db-docker-restore.sh — restore le dump prod dans le container Postgres Docker.
#
# Usage :
#   ./scripts/db-docker-restore.sh                          # auto-détecte dernier dump
#   ./scripts/db-docker-restore.sh ~/Documents/oneclick-prod-20260506-111655
#
# Pré-requis :
#   1. docker compose up -d   (postgres healthcheck = ready)
#   2. Un dump prod dans ~/Documents/oneclick-prod-* (généré par scripts/backup.sh)
#
# Idempotent : drop la DB + recrée + restore. Tout fait perso est perdu.

set -euo pipefail
cd "$(dirname "$0")/.."

CONTAINER="oneclick_postgres"
DB_NAME="oneclick_local"
DB_OWNER="hh"

# ── 1. Vérifier que le container postgres est up ────────────────────
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "✗ Container ${CONTAINER} pas démarré. Lance d'abord :"
  echo "    docker compose up -d"
  exit 1
fi

if ! docker exec "${CONTAINER}" pg_isready -U "${DB_OWNER}" -d postgres >/dev/null 2>&1; then
  echo "✗ Postgres dans ${CONTAINER} pas prêt (attends quelques secondes après up)."
  exit 1
fi
echo "✓ Container ${CONTAINER} ready"

# ── 2. Trouver le dump prod ─────────────────────────────────────────
# On cherche un DOSSIER (pas un .tar.gz) qui contient db-full.dump.
# Plusieurs candidats peuvent exister (duplicates Finder " 2" etc.) — on
# prend le plus récent qui satisfait les 2 conditions.
if [[ $# -ge 1 ]]; then
  DUMP_DIR="$1"
else
  DUMP_DIR=""
  while IFS= read -r -d '' candidate; do
    if [[ -f "${candidate}/db-full.dump" ]]; then
      DUMP_DIR="${candidate}"
      break
    fi
  done < <(find "${HOME}/Documents" -maxdepth 1 -type d -name "oneclick-prod-*" -print0 2>/dev/null \
           | xargs -0 stat -f "%m %N" 2>/dev/null | sort -rn | cut -d' ' -f2- | tr '\n' '\0')
fi

if [[ -z "${DUMP_DIR:-}" ]] || [[ ! -d "${DUMP_DIR}" ]]; then
  echo "✗ Pas de dump trouvé. Cherché : un dossier ~/Documents/oneclick-prod-*"
  echo "  contenant db-full.dump."
  echo
  echo "  Disponibles :"
  ls -d "${HOME}"/Documents/oneclick-prod-* 2>/dev/null | sed 's/^/    /'
  echo
  echo "  Soit lance scripts/backup.sh d'abord, soit passe le path explicite :"
  echo "    ./scripts/db-docker-restore.sh \"/path/to/dump-dir\""
  exit 1
fi

DUMP_FILE="${DUMP_DIR}/db-full.dump"
if [[ ! -f "${DUMP_FILE}" ]]; then
  echo "✗ Pas de db-full.dump dans ${DUMP_DIR}"
  exit 1
fi

DUMP_SIZE=$(du -h "${DUMP_FILE}" | cut -f1)
echo "✓ Dump trouvé : ${DUMP_FILE} (${DUMP_SIZE})"

# ── 3. Drop + recreate la DB (clean slate) ─────────────────────────
echo "→ Drop + recreate ${DB_NAME}..."
docker exec "${CONTAINER}" psql -U "${DB_OWNER}" -d postgres -c \
  "DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE);" >/dev/null
docker exec "${CONTAINER}" psql -U "${DB_OWNER}" -d postgres -c \
  "CREATE DATABASE ${DB_NAME} OWNER ${DB_OWNER};" >/dev/null
echo "✓ DB ${DB_NAME} recréée"

# ── 4. Schema auth (Supabase shim) ──────────────────────────────────
docker exec "${CONTAINER}" psql -U "${DB_OWNER}" -d "${DB_NAME}" -c \
  "CREATE SCHEMA IF NOT EXISTS auth;" >/dev/null

# ── 5. Restore via pg_restore ───────────────────────────────────────
echo "→ Restore en cours (peut prendre 1-3 min)..."
# Copie temporaire dans le container (plus rapide que stream --no-tty over stdin)
docker cp "${DUMP_FILE}" "${CONTAINER}:/tmp/db-full.dump"
# pg_restore : --no-owner --no-privileges car on n'a pas les rôles Supabase
# (authenticated, service_role, anon) → erreurs ignorées.
docker exec "${CONTAINER}" pg_restore \
  -U "${DB_OWNER}" -d "${DB_NAME}" \
  --no-owner --no-privileges \
  /tmp/db-full.dump 2>&1 | tail -5 || true
docker exec "${CONTAINER}" rm /tmp/db-full.dump

# ── 6. Grant privileges sur le user app + BYPASSRLS ─────────────────
echo "→ Configuration oneclick_app (privileges + BYPASSRLS)..."
docker exec "${CONTAINER}" psql -U "${DB_OWNER}" -d "${DB_NAME}" <<'SQL' >/dev/null
GRANT USAGE ON SCHEMA public, auth TO oneclick_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO oneclick_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO oneclick_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO oneclick_app;
GRANT SELECT ON ALL TABLES IN SCHEMA auth TO oneclick_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO oneclick_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO oneclick_app;
ALTER ROLE oneclick_app BYPASSRLS;
SQL

# ── 7. Verif ────────────────────────────────────────────────────────
COUNT_RESTOS=$(docker exec "${CONTAINER}" psql -U "${DB_OWNER}" -d "${DB_NAME}" -t -A -c \
  "SELECT count(*) FROM public.restaurants" 2>/dev/null)
COUNT_USERS=$(docker exec "${CONTAINER}" psql -U "${DB_OWNER}" -d "${DB_NAME}" -t -A -c \
  "SELECT count(*) FROM auth.users" 2>/dev/null)

echo
echo "✓ Restore complet"
echo "  restaurants : ${COUNT_RESTOS}  (attendu ~1042)"
echo "  auth.users  : ${COUNT_USERS}  (attendu ~17221)"
echo
echo "→ Lance maintenant Spring contre cette DB Docker :"
echo "    ./run.sh             (mode dev)"
echo "    ./run.sh --oauth2    (mode oauth2)"
