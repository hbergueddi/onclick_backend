#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════
# run-vps.sh — Lance le JAR OneClick (profil prod) contre une COPIE de
#              notre base oneclick_enterprise (sauvegarde du 9 juin ≈ V95).
# ════════════════════════════════════════════════════════════════════
# ⚠️  Le JAR est en V99. Au boot, Flyway appliquera EN AVANT les migrations
#     manquantes V96 → V99 sur TES données, dont :
#       V96  resources : +3 colonnes (additif) + durées/invités par type
#       V97  announcements : grant RBAC (additif)
#       V98  REBRAND PALIERS : renomme les tiers + change les seuils (200/500/1000/50000) + ajoute "Ambassadeur"
#       V99  grant RBAC tier->restaurateur (additif)
#     V96-V99 sont du CODE NON COMMITTÉ (état dev de pointe).
#
# Ce script FORCE un pg_dump de sécurité AVANT tout boot, et demande une
# confirmation explicite (sauf --yes).
#
# Prérequis : Postgres joignable, Redis sur :6379, pg_dump/psql dans le PATH.
# Variables OBLIGATOIRES (non gravées dans ce fichier) :
#   DB_PASSWORD               mot de passe Postgres
#   APP_SECURITY_JWT_SECRET   secret HS256 (>=256 bits) qui signe les tokens
#
# Usage :
#   DB_PASSWORD='<DB_PASSWORD>' APP_SECURITY_JWT_SECRET='...' ./run-vps.sh
#   DB_PASSWORD='<DB_PASSWORD>' APP_SECURITY_JWT_SECRET='...' ./run-vps.sh --yes   # sans prompt
# ════════════════════════════════════════════════════════════════════
set -euo pipefail

# ─── Paramètres (non secrets : défauts surchargeables) ───────────────
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-oneclick}"
DB_USER="${DB_USERNAME:-postgres}"
JAR="${JAR:-oneclick-0.0.1-SNAPSHOT.jar}"

# ─── Garde-fous ──────────────────────────────────────────────────────
[[ -f "$JAR" ]] || { echo "✗ JAR introuvable : $JAR (place ce script à côté du .jar)"; exit 1; }
[[ -n "${DB_PASSWORD:-}" ]]             || { echo "✗ DB_PASSWORD non défini";             exit 1; }
[[ -n "${APP_SECURITY_JWT_SECRET:-}" ]] || { echo "✗ APP_SECURITY_JWT_SECRET non défini (obligatoire en profil prod)"; exit 1; }
export PGPASSWORD="$DB_PASSWORD"
CONN="host=$DB_HOST port=$DB_PORT user=$DB_USER dbname=$DB_NAME"

# ─── (optionnel) Redis : avertit si injoignable (cache.type=redis) ───
if command -v redis-cli >/dev/null 2>&1; then
  redis-cli -h "${REDIS_HOST:-localhost}" -p "${REDIS_PORT:-6379}" ping >/dev/null 2>&1 \
    && echo "→ Redis OK" \
    || echo "⚠️  Redis injoignable sur ${REDIS_HOST:-localhost}:${REDIS_PORT:-6379} — le profil prod en a besoin au boot."
fi

# ─── 1. Pré-flight : version Flyway actuelle de la base ──────────────
echo "→ Pré-flight Flyway sur ${DB_NAME}@${DB_HOST}:${DB_PORT} ..."
CUR=$(psql "$CONN" -tAc \
  "SELECT coalesce(max(version)::text,'(aucune ligne)') FROM flyway_schema_history WHERE version ~ '^[0-9]+\$';" 2>/dev/null || echo "ERR")
if [[ "$CUR" == "ERR" ]]; then
  echo "✗ Connexion DB échouée OU table flyway_schema_history absente."
  echo "  → Si la copie n'a PAS d'historique Flyway, NE force PAS de baseline=1 : préviens-moi (baseline à la vraie version)."
  exit 1
fi
echo "   version base = V$CUR   |   version JAR = V99   →   migrations à appliquer : V$((CUR+1))..V99"

# ─── 2. Backup de sécurité OBLIGATOIRE ───────────────────────────────
DUMP="oneclick_${DB_NAME}_$(date +%Y%m%d_%H%M%S).dump"
echo "→ pg_dump de sécurité → $DUMP"
pg_dump "$CONN" -Fc -f "$DUMP"
echo "   ✓ backup OK ($(du -h "$DUMP" | cut -f1))   |   restauration : pg_restore -c -d $DB_NAME \"$DUMP\""

# ─── 3. Confirmation (sauf --yes) ────────────────────────────────────
if [[ "${1:-}" != "--yes" ]]; then
  echo
  echo "⚠️  Le boot va appliquer V$((CUR+1))..V99 sur '$DB_NAME' (dont V98 = rebrand des paliers de fidélité)."
  read -r -p "    Tape MIGRATE pour continuer (autre chose = annuler) : " ANS
  [[ "$ANS" == "MIGRATE" ]] || { echo "Annulé. (Aucune migration appliquée — le backup $DUMP reste disponible.)"; exit 0; }
fi

# ─── 4. Boot (profil prod ; FCM réel coupé par sécurité) ─────────────
echo "→ Boot profil prod sur :8080 ..."
exec env \
  DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
  DB_USERNAME="$DB_USER" \
  DB_PASSWORD="$DB_PASSWORD" \
  FCM_DISPATCH_ENABLED=false \
  java -jar "$JAR" --spring.profiles.active=prod
