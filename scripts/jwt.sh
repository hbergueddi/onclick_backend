#!/usr/bin/env bash
# jwt.sh — génère un JWT HS256 valide pour le backend Spring (mode oauth2).
#
# Usage :
#   ./scripts/jwt.sh                       # admin par défaut
#   ./scripts/jwt.sh admin                 # un user avec rôle admin
#   ./scripts/jwt.sh client                # un user avec rôle client
#   ./scripts/jwt.sh restaurateur          # un user avec rôle restaurateur
#   ./scripts/jwt.sh tenant_admin          # un user avec rôle tenant_admin
#   ./scripts/jwt.sh <uuid-spécifique>     # JWT pour un user_id donné
#
# Pré-requis :
#   - .env avec APP_SECURITY_JWT_SECRET défini
#   - Postgres local accessible (PGUSER/PGDATABASE par défaut hh/oneclick_local)

set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f ".env" ]]; then
  echo "✗ .env introuvable. Crée-le depuis .env.example et mets ton APP_SECURITY_JWT_SECRET dedans."
  exit 1
fi

SECRET=$(grep '^APP_SECURITY_JWT_SECRET=' .env | cut -d'=' -f2-)
if [[ -z "${SECRET}" ]]; then
  echo "✗ APP_SECURITY_JWT_SECRET absent du .env"
  exit 1
fi

ARG="${1:-admin}"

# Cas 1 : UUID directement fourni
if [[ "${ARG}" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
  USER_ID="${ARG}"
  ROLE_INFO="(direct UUID)"
else
  # Cas 2 : nom de rôle — pioche un user random qui a ce rôle
  case "${ARG}" in
    admin|client|restaurateur|tenant_admin) ;;
    *)
      echo "✗ Argument invalide : '${ARG}'"
      echo "  Usage : ./scripts/jwt.sh [admin|client|restaurateur|tenant_admin|<uuid>]"
      exit 1
      ;;
  esac
  USER_ID=$(psql -d "${PGDATABASE:-oneclick_local}" -t -A -c \
    "SELECT user_id FROM user_roles WHERE role='${ARG}' ORDER BY random() LIMIT 1" 2>/dev/null || true)
  if [[ -z "${USER_ID}" ]]; then
    echo "✗ Aucun user avec rôle '${ARG}' dans user_roles"
    exit 1
  fi
  ROLE_INFO="(role=${ARG})"
fi

JWT=$(SECRET="${SECRET}" USER_ID="${USER_ID}" node -e "
const crypto = require('crypto');
const secret = process.env.SECRET;
const sub = process.env.USER_ID;
if (!secret) { console.error('SECRET env missing'); process.exit(1); }
if (!sub)    { console.error('USER_ID env missing'); process.exit(1); }
const h = Buffer.from(JSON.stringify({alg:'HS256',typ:'JWT'})).toString('base64url');
const p = Buffer.from(JSON.stringify({
  iss: 'oneclick-test',
  sub,
  iat: Math.floor(Date.now()/1000),
  exp: Math.floor(Date.now()/1000) + 3600,
  role: 'authenticated'
})).toString('base64url');
console.log(h + '.' + p + '.' + crypto.createHmac('sha256', secret).update(h + '.' + p).digest('base64url'));
")

echo "user_id : ${USER_ID} ${ROLE_INFO}"
echo "JWT     : ${JWT}"
echo
echo "Usage curl :"
echo "  curl -H \"Authorization: Bearer \${JWT}\" http://localhost:8081/api/tenants"
echo
echo "Export shell :"
echo "  export JWT='${JWT}'"
