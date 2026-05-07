#!/usr/bin/env bash
# test-e2e.sh — lance une batterie de tests E2E sur tout ce que le backend
# expose actuellement. Suppose que Spring tourne en mode --oauth2.
#
# Usage :
#   ./run.sh --oauth2 &                  # terminal 1
#   ./scripts/test-e2e.sh                # terminal 2

set -uo pipefail
cd "$(dirname "$0")/.."

BASE=http://localhost:8081
PASS=0
FAIL=0

assert() {
  local label=$1
  local expected=$2
  local actual=$3
  if [[ "${actual}" == "${expected}" ]]; then
    echo "  ✓ ${label} (HTTP ${actual})"
    PASS=$((PASS+1))
  else
    echo "  ✗ ${label} (expected HTTP ${expected}, got ${actual})"
    FAIL=$((FAIL+1))
  fi
}

http_code() {
  curl -s -o /dev/null -w "%{http_code}" "$@"
}

echo "════════════════════════════════════════════════════════════════"
echo "  Test E2E backend OneClick Spring"
echo "════════════════════════════════════════════════════════════════"
echo

echo "── Pré-requis ──────────────────────────────────────────────────"
if ! curl -s -o /dev/null -m 2 "${BASE}/actuator/health"; then
  echo "✗ Backend non joignable sur ${BASE}. Lance ./run.sh --oauth2 d'abord."
  exit 1
fi
echo "  ✓ Backend up"
echo

echo "── 1. Endpoints publics (pas de JWT requis) ────────────────────"
assert "GET /actuator/health"        "200" "$(http_code ${BASE}/actuator/health)"
assert "GET /actuator/flyway"        "401" "$(http_code ${BASE}/actuator/flyway)"
assert "GET /actuator/info"          "200" "$(http_code ${BASE}/actuator/info)"
assert "GET /v3/api-docs"            "200" "$(http_code ${BASE}/v3/api-docs)"
assert "GET /swagger-ui/index.html"  "200" "$(http_code ${BASE}/swagger-ui/index.html)"
echo

echo "── 2. Sécurité — sans JWT, les endpoints sécurisés rejettent ───"
assert "GET /api/tenants no JWT"                 "401" "$(http_code ${BASE}/api/tenants)"
assert "GET /api/restaurants/core/by-city no"    "401" "$(http_code ${BASE}/api/restaurants/core/by-city?city=Casablanca)"
echo

echo "── 3. Génération de 3 JWT (admin / restaurateur / client) ──────"
ADMIN_JWT=$(./scripts/jwt.sh admin 2>/dev/null | grep ^JWT | awk '{print $3}')
RESTAU_JWT=$(./scripts/jwt.sh restaurateur 2>/dev/null | grep ^JWT | awk '{print $3}')
CLIENT_JWT=$(./scripts/jwt.sh client 2>/dev/null | grep ^JWT | awk '{print $3}')
echo "  ✓ JWTs générés (${#ADMIN_JWT}+${#RESTAU_JWT}+${#CLIENT_JWT} chars)"
echo

echo "── 4. Permissions par rôle ─────────────────────────────────────"
assert "admin → /api/tenants"                       "200" "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/tenants)"
assert "client → /api/tenants (no permission)"      "403" "$(http_code -H "Authorization: Bearer ${CLIENT_JWT}" ${BASE}/api/tenants)"
assert "restaurateur → /api/admin-audit-logs"       "403" "$(http_code -H "Authorization: Bearer ${RESTAU_JWT}" ${BASE}/api/admin-audit-logs)"
assert "admin → /api/admin-audit-logs"              "200" "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/admin-audit-logs)"
assert "client → /api/restaurants/core/by-city=Casa" "200" "$(http_code -H "Authorization: Bearer ${CLIENT_JWT}" ${BASE}/api/restaurants/core/by-city?city=Casablanca)"
assert "restaurateur → /api/loyalty-points"         "200" "$(http_code -H "Authorization: Bearer ${RESTAU_JWT}" ${BASE}/api/loyalty-points)"
assert "client → /api/partner-contracts (admin)"    "403" "$(http_code -H "Authorization: Bearer ${CLIENT_JWT}" ${BASE}/api/partner-contracts)"
echo

echo "── 5. Lectures business (admin a accès partout) ────────────────"
assert "GET /api/tenants"                "200" "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/tenants)"
assert "GET /api/offers"                 "200" "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/offers)"
assert "GET /api/loyalty-points"         "200" "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/loyalty-points)"
assert "GET /api/scanned-tickets"        "200" "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/scanned-tickets)"
assert "GET /api/bookable-resources"     "200" "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/bookable-resources)"
assert "GET /api/notifications"          "200" "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/notifications)"
assert "GET /api/views/admin-audit-logs" "200" "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/views/admin-audit-logs)"
echo

echo "── 6. Recherche dynamique (Specifications Phase 6.3) ───────────"
SEARCH_RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CLIENT_JWT}" \
  -d '{"criteria":[{"field":"firstName","op":"ILIKE","value":"You%"}],"size":3}' \
  ${BASE}/api/profiles/search)
COUNT=$(echo "${SEARCH_RESULT}" | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('content',[])))" 2>/dev/null || echo 0)
if [[ "${COUNT}" -gt 0 ]]; then
  echo "  ✓ POST /api/profiles/search firstName ILIKE 'You%' → ${COUNT} résultats"
  PASS=$((PASS+1))
else
  echo "  ✗ POST /api/profiles/search → 0 résultats"
  FAIL=$((FAIL+1))
fi

assert "POST /api/profiles/search field non-whitelisté → 400" "400" \
  "$(http_code -X POST -H "Content-Type: application/json" -H "Authorization: Bearer ${CLIENT_JWT}" \
       -d '{"criteria":[{"field":"badField","op":"EQ","value":"x"}]}' \
       ${BASE}/api/profiles/search)"
echo

echo "── 7. Validation Bean (Phase 6.1) ──────────────────────────────"
SAMPLE_PROFILE=$(psql -d oneclick_local -t -A -c "SELECT id FROM profiles WHERE first_name<>'' LIMIT 1")
assert "PATCH /api/profiles/{id} phone format invalid → 400" "400" \
  "$(http_code -X PATCH -H "Content-Type: application/json" -H "Authorization: Bearer ${CLIENT_JWT}" \
       -d '{"phone":"INVALID-PHONE!!"}' \
       ${BASE}/api/profiles/${SAMPLE_PROFILE})"
assert "PATCH /api/profiles/{id} city OK → 200" "200" \
  "$(http_code -X PATCH -H "Content-Type: application/json" -H "Authorization: Bearer ${CLIENT_JWT}" \
       -d '{"city":"Tanger"}' \
       ${BASE}/api/profiles/${SAMPLE_PROFILE})"
assert "GET /api/profiles/00000000-0000-0000-0000-000000000099 → 404" "404" \
  "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/profiles/00000000-0000-0000-0000-000000000099)"
assert "GET /api/reservations/not-a-uuid → 400" "400" \
  "$(http_code -H "Authorization: Bearer ${ADMIN_JWT}" ${BASE}/api/reservations/not-a-uuid)"
echo

echo "── 8. Events (Phase 5.3) ────────────────────────────────────────"
echo "  → PATCH profile devrait publier ProfileUpdatedEvent"
echo "  → check les logs Spring : grep '[AUDIT]' /tmp/oneclick-boot.log"
echo

echo "════════════════════════════════════════════════════════════════"
echo "  Résultat : ${PASS} passed, ${FAIL} failed"
echo "════════════════════════════════════════════════════════════════"
exit ${FAIL}
