#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════
# enrich-all-google-places.sh — Batch enrichment Google Places
# ════════════════════════════════════════════════════════════════════
#
# Loope sur tous les restaurants `active` non encore enrichis
# (google_place_id IS NULL) et appelle POST /api/restaurants/{id}/enrich-google-places.
#
# Pré-requis :
#   1. Spring backend tourne sur :8083 avec app.google.places.api-key configurée
#   2. Docker postgres tourne (sinon les SELECT pgsql cassent)
#   3. Login admin@m3ak.com / TestLocal2026!
#
# Throttling : 250ms entre 2 calls (4 req/s) — safe vs Google Places quota.
# Total estimé : ~1042 restos * 0.25s = ~4-5 min.
#
# Idempotent : skippe automatiquement les restos déjà enrichis (sauf si --force).
#
# Usage :
#   bash scripts/enrich-all-google-places.sh           # tous les non-enrichis
#   bash scripts/enrich-all-google-places.sh --force   # tous, même déjà enrichis
#   bash scripts/enrich-all-google-places.sh --city Casablanca  # filtre ville
# ════════════════════════════════════════════════════════════════════

set -euo pipefail

API_URL="${API_URL:-http://localhost:8083}"
PG_HOST="${PG_HOST:-localhost}"
PG_USER="${PG_USER:-oneclick_app}"
PG_DB="${PG_DB:-oneclick_enterprise}"
PG_PASSWORD="${PGPASSWORD:-OneclickLocal2026}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@m3ak.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-TestLocal2026!}"
THROTTLE_MS="${THROTTLE_MS:-250}"

# ─── Parse args ──────────────────────────────────────────────────────
FORCE=""
CITY_FILTER=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force) FORCE="?force=true" ; shift ;;
    --city) CITY_FILTER="$2" ; shift 2 ;;
    *) echo "Unknown arg: $1" ; exit 1 ;;
  esac
done

# ─── 1. Sanity checks ────────────────────────────────────────────────
echo "════════════════════════════════════════════════════════════════"
echo "  Google Places batch enrichment"
echo "════════════════════════════════════════════════════════════════"
echo "  Backend  : $API_URL"
echo "  DB       : $PG_USER@$PG_HOST/$PG_DB"
echo "  Force    : ${FORCE:-non (skip déjà enrichis)}"
echo "  City     : ${CITY_FILTER:-toutes}"
echo "  Throttle : ${THROTTLE_MS}ms entre calls"
echo

if ! curl -sf "$API_URL/actuator/health" > /dev/null; then
  echo "✗ Backend pas joignable sur $API_URL" >&2
  exit 1
fi

# ─── 2. Login admin ──────────────────────────────────────────────────
echo "→ Login admin..."
TOKEN=$(curl -sf -X POST "$API_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  --data-binary "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))")

if [[ -z "$TOKEN" ]]; then
  echo "✗ Login échoué" >&2 ; exit 1
fi
echo "  ✓ Token OK (len=${#TOKEN})"

# ─── 3. Quick API key check (1 ping silencieux) ──────────────────────
PROBE_ID=$(PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -U "$PG_USER" -d "$PG_DB" -At \
  -c "SELECT id FROM restaurants WHERE status='active' AND deleted_at IS NULL LIMIT 1;")
PROBE=$(curl -sf -X POST "$API_URL/api/restaurants/$PROBE_ID/enrich-google-places$FORCE" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('reason','OK'))" 2>/dev/null || echo "ERROR")
if [[ "$PROBE" == "no_api_key" ]]; then
  echo "✗ Backend n'a pas la clé Google Places — vérifie app.google.places.api-key" >&2
  exit 1
fi
echo "  ✓ API key OK (probe response: $PROBE)"
echo

# ─── 4. Liste les restos à enrichir ──────────────────────────────────
WHERE="status='active' AND deleted_at IS NULL"
[[ -z "$FORCE" ]] && WHERE="$WHERE AND google_place_id IS NULL"
[[ -n "$CITY_FILTER" ]] && WHERE="$WHERE AND city=\$\$$CITY_FILTER\$\$"

RESTAURANTS_FILE=$(mktemp)
PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -U "$PG_USER" -d "$PG_DB" -At -F'|' \
  -c "SELECT id, name, city FROM restaurants WHERE $WHERE ORDER BY city, name;" > "$RESTAURANTS_FILE"
TOTAL=$(wc -l < "$RESTAURANTS_FILE" | tr -d ' ')

if [[ $TOTAL -eq 0 ]]; then
  echo "Rien à enrichir (tous déjà OK ou aucun match)."
  rm -f "$RESTAURANTS_FILE"
  exit 0
fi
echo "→ $TOTAL restaurants à enrichir"
echo

# ─── 5. Loop + throttle ──────────────────────────────────────────────
ENRICHED=0 ; SKIPPED=0 ; NOTFOUND=0 ; ERRORED=0 ; I=0
START=$(date +%s)

while IFS= read -r row; do
  I=$((I+1))
  IFS='|' read -r ID NAME CITY <<<"$row"
  PROGRESS=$(printf "[%4d/%4d]" "$I" "$TOTAL")

  RESPONSE=$(curl -sf -X POST "$API_URL/api/restaurants/$ID/enrich-google-places$FORCE" \
    -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo '{"error":"http_fail"}')

  STATUS=$(echo "$RESPONSE" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    if d.get('enriched'): print('ENRICHED')
    elif d.get('reason') == 'not_found_on_google': print('NOTFOUND')
    elif d.get('skipped'): print('SKIPPED:' + str(d.get('reason','')))
    elif d.get('error'): print('ERROR:' + str(d.get('error','')))
    else: print('OK')
except: print('PARSE_FAIL')
" 2>/dev/null)

  case "$STATUS" in
    ENRICHED) ENRICHED=$((ENRICHED+1)) ; echo "$PROGRESS ✓ $NAME ($CITY)" ;;
    NOTFOUND) NOTFOUND=$((NOTFOUND+1)) ; echo "$PROGRESS · $NAME ($CITY) → introuvable Google" ;;
    SKIPPED:*) SKIPPED=$((SKIPPED+1)) ;;  # silencieux
    *) ERRORED=$((ERRORED+1)) ; echo "$PROGRESS ✗ $NAME ($CITY) → $STATUS" ;;
  esac

  # Throttle (sleep en secondes float — python pour éviter dépendance bc)
  sleep "$(python3 -c "print($THROTTLE_MS / 1000)")"
done < "$RESTAURANTS_FILE"
rm -f "$RESTAURANTS_FILE"

# ─── 6. Stats finales ────────────────────────────────────────────────
END=$(date +%s) ; DURATION=$((END-START))
echo
echo "════════════════════════════════════════════════════════════════"
echo "  Résultat : $TOTAL restos traités en ${DURATION}s"
echo "    ✓ Enrichis     : $ENRICHED"
echo "    · Introuvables : $NOTFOUND  (nom/adresse pas matchés par Google)"
echo "    · Skippés      : $SKIPPED  (déjà enrichis, sauf --force)"
echo "    ✗ Erreurs      : $ERRORED"
echo "════════════════════════════════════════════════════════════════"

# ─── 7. Diff DB après vs avant ───────────────────────────────────────
echo
echo "  DB after :"
PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -U "$PG_USER" -d "$PG_DB" -At -c "
SELECT '    google_place_id:    ' || COUNT(*) FROM restaurants WHERE google_place_id IS NOT NULL AND deleted_at IS NULL;
SELECT '    google_rating:      ' || COUNT(*) FROM restaurants WHERE google_rating IS NOT NULL AND deleted_at IS NULL;
SELECT '    opening_hours:      ' || COUNT(*) FROM restaurants WHERE opening_hours IS NOT NULL AND deleted_at IS NULL;
SELECT '    website_url:        ' || COUNT(*) FROM restaurants WHERE website_url IS NOT NULL AND deleted_at IS NULL;
"
