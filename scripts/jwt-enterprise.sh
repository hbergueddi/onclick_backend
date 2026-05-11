#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# jwt-enterprise.sh — JWT HS256 pour le schéma enterprise (users.role_id)
# ══════════════════════════════════════════════════════════════════════════
# Diffère de jwt.sh : utilise `users` + `roles` (pas `user_roles`).
# Compatible profil "secure" + JWT_SECRET du même nom.
#
# Usage :
#   ./scripts/jwt-enterprise.sh                       # SUPERADMIN par défaut
#   ./scripts/jwt-enterprise.sh SUPERADMIN
#   ./scripts/jwt-enterprise.sh CLIENT
#   ./scripts/jwt-enterprise.sh RESTAURATEUR
#   ./scripts/jwt-enterprise.sh <uuid-spécifique>
#
# DB par défaut : oneclick_enterprise (override via PGDATABASE env var).
# ══════════════════════════════════════════════════════════════════════════

set -euo pipefail

SECRET="${JWT_SECRET:-onesley-oneclick-dev-secret-256-bits-minimum-length-required}"
ARG="${1:-SUPERADMIN}"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-oneclick_enterprise}"
PGUSER="${PGUSER:-oneclick_app}"
export PGPASSWORD="${PGPASSWORD:-OneclickLocal2026}"

if [[ "${ARG}" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
  USER_ID="${ARG}"
  ROLE_INFO="(direct UUID)"
else
  USER_ID=$(psql -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -tAc \
    "SELECT u.id FROM users u JOIN roles r ON r.id = u.role_id WHERE r.code='${ARG}' AND u.deleted_at IS NULL ORDER BY random() LIMIT 1" 2>/dev/null || true)
  if [[ -z "${USER_ID}" ]]; then
    echo "✗ Aucun user avec rôle '${ARG}'"
    exit 1
  fi
  ROLE_INFO="(role=${ARG})"
fi

JWT=$(SECRET="${SECRET}" USER_ID="${USER_ID}" node -e "
const crypto = require('crypto');
const secret = process.env.SECRET;
const sub = process.env.USER_ID;
const h = Buffer.from(JSON.stringify({alg:'HS256',typ:'JWT'})).toString('base64url');
const p = Buffer.from(JSON.stringify({
  iss: 'oneclick-enterprise',
  sub,
  iat: Math.floor(Date.now()/1000),
  exp: Math.floor(Date.now()/1000) + 3600,
  role: 'authenticated'
})).toString('base64url');
console.log(h + '.' + p + '.' + crypto.createHmac('sha256', secret).update(h + '.' + p).digest('base64url'));
")

echo "user_id : ${USER_ID} ${ROLE_INFO}"
echo "JWT     : ${JWT}"
echo ""
echo "Test :"
echo "  curl -H \"Authorization: Bearer ${JWT}\" http://localhost:8083/api/users/me"
echo "  curl -H \"Authorization: Bearer ${JWT}\" http://localhost:8080/api/users/me  # via gateway"
