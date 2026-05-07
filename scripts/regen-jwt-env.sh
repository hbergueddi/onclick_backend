#!/usr/bin/env bash
# regen-jwt-env.sh — régénère 4 JWT (un par rôle) et les écrit dans
# http-client.private.env.json. À lancer avant chaque session de test
# IntelliJ HTTP Client (les JWT expirent après 1h).
#
# Usage :
#   ./scripts/regen-jwt-env.sh
#
# Pré-requis :
#   - .env avec APP_SECURITY_JWT_SECRET défini
#   - Postgres local (oneclick_local) avec data seedée

set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -f ".env" ]]; then
  echo "✗ .env introuvable"
  exit 1
fi

SECRET=$(grep '^APP_SECURITY_JWT_SECRET=' .env | cut -d'=' -f2-)
if [[ -z "${SECRET}" ]]; then
  echo "✗ APP_SECURITY_JWT_SECRET absent du .env"
  exit 1
fi

# Connexion psql — TCP localhost (compatible Docker + brew)
# Avec Docker : pas de socket UNIX, donc -h localhost obligatoire.
# Avec brew  : marche aussi via TCP localhost (port 5432 lib).
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-oneclick_local}"
PGUSER="${PGUSER:-oneclick_app}"
export PGPASSWORD="${PGPASSWORD:-OneclickLocal2026}"

PSQL_CMD=(psql -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -t -A)

# Pioche un user_id par rôle
ADMIN_ID=$("${PSQL_CMD[@]}" -c "SELECT user_id FROM user_roles WHERE role='admin' ORDER BY random() LIMIT 1")
CLIENT_ID=$("${PSQL_CMD[@]}" -c "SELECT user_id FROM user_roles WHERE role='client' ORDER BY random() LIMIT 1")
RESTAU_ID=$("${PSQL_CMD[@]}" -c "SELECT user_id FROM user_roles WHERE role='restaurateur' ORDER BY random() LIMIT 1")
TENANT_ID=$("${PSQL_CMD[@]}" -c "SELECT user_id FROM user_roles WHERE role='tenant_admin' ORDER BY random() LIMIT 1")

if [[ -z "${ADMIN_ID}" || -z "${CLIENT_ID}" || -z "${RESTAU_ID}" ]]; then
  echo "✗ Impossible de récupérer les user_id depuis ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE}"
  echo "  Vérifie que le container/serveur Postgres tourne et que la data est restorée."
  exit 1
fi

# Génère un JWT HS256 via Node (env vars en input)
gen_jwt() {
  local sub=$1
  SECRET="${SECRET}" SUB="${sub}" node -e '
    const crypto = require("crypto");
    const secret = process.env.SECRET;
    const sub = process.env.SUB;
    const h = Buffer.from(JSON.stringify({alg:"HS256",typ:"JWT"})).toString("base64url");
    const now = Math.floor(Date.now() / 1000);
    const p = Buffer.from(JSON.stringify({
      iss: "oneclick-test", sub, iat: now, exp: now + 3600, role: "authenticated"
    })).toString("base64url");
    console.log(h + "." + p + "." + crypto.createHmac("sha256", secret).update(h + "." + p).digest("base64url"));
  '
}

JWT_ADMIN=$(gen_jwt "${ADMIN_ID}")
JWT_CLIENT=$(gen_jwt "${CLIENT_ID}")
JWT_RESTAU=$(gen_jwt "${RESTAU_ID}")
JWT_TENANT=$(gen_jwt "${TENANT_ID:-${ADMIN_ID}}")  # fallback admin si pas de tenant_admin seed

cat > http-client.private.env.json <<EOF
{
  "oauth2": {
    "jwtSecret": "${SECRET}",
    "adminUserId": "${ADMIN_ID}",
    "clientUserId": "${CLIENT_ID}",
    "restaurateurUserId": "${RESTAU_ID}",
    "tenantAdminUserId": "${TENANT_ID}",
    "jwtAdmin": "${JWT_ADMIN}",
    "jwtClient": "${JWT_CLIENT}",
    "jwtRestaurateur": "${JWT_RESTAU}",
    "jwtTenantAdmin": "${JWT_TENANT}"
  }
}
EOF

echo "✓ http-client.private.env.json régénéré (JWT valides 1h)"
echo "  jwtAdmin       : ${ADMIN_ID}"
echo "  jwtClient      : ${CLIENT_ID}"
echo "  jwtRestaurateur: ${RESTAU_ID}"
echo "  jwtTenantAdmin : ${TENANT_ID:-(fallback admin)}"
echo
echo "→ Dans IntelliJ, ouvre/recharge api-tests.http (les variables sont"
echo "  pickées automatiquement). Re-lance ce script si une requête"
echo "  retourne 401 (JWT expiré)."
