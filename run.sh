#!/usr/bin/env bash
# run.sh — Lance le monolithe OneClick avec Java 26 sans toucher au $JAVA_HOME global.
#
# Utile sur les machines où JAVA_HOME pointe ailleurs (ex: Android Studio JBR
# en Java 21 sur les Mac avec Capacitor).
#
# Profil par défaut : `enterprise` → DB oneclick_enterprise, port 8083.
#
# Usage:
#   ./run.sh                       # monolith profil enterprise (port 8083)
#   ./run.sh --secure              # monolith profil enterprise,secure (OAuth2 JWT)
#   ./run.sh --dev                 # legacy profil dev (DB oneclick_local, port 8081)
#   APP_SECURITY_JWT_SECRET=xxx ./run.sh --secure   # avec ton secret JWT

set -euo pipefail

JAVA_HOME=$(/usr/libexec/java_home -v 26 2>/dev/null || true)
if [[ -z "${JAVA_HOME}" ]]; then
  echo "✗ Java 26 introuvable. Installe-le via :"
  echo "    brew install openjdk@26      # ou téléchargement manuel openjdk.org"
  exit 1
fi
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

echo "→ JAVA_HOME = ${JAVA_HOME}"
echo "→ $(java -version 2>&1 | head -1)"

# ─── Résolution profil + port ────────────────────────────────────────
PROFILES="enterprise"
PORT=8083
MODE="enterprise"

case "${1:-}" in
  --dev)
    PROFILES="dev"
    PORT=8081
    MODE="dev (legacy DB oneclick_local)"
    ;;
  --secure)
    PROFILES="enterprise,secure"
    PORT=8083
    MODE="enterprise + OAuth2 JWT"
    ;;
  "")
    ;;
  *)
    echo "✗ Argument inconnu: $1"
    echo "  Usage: ./run.sh [--dev|--secure]"
    exit 1
    ;;
esac

# Pre-flight : kill un Spring Boot précédent s'il squatte le port
if lsof -ti :${PORT} >/dev/null 2>&1; then
  PIDS=$(lsof -ti :${PORT} 2>/dev/null || true)
  echo "→ port ${PORT} occupé (PIDs: ${PIDS}) — kill"
  pkill -f "spring-boot:run" 2>/dev/null || true
  pkill -f "OneClickSpringApplication" 2>/dev/null || true
  echo "${PIDS}" | xargs -r kill -9 2>/dev/null || true
  sleep 2
fi

# Auto-source .env si présent (gitignored — pour les secrets locaux)
if [[ -f ".env" ]]; then
  set -a
  source ./.env
  set +a
  env_lines=$(wc -l < .env | tr -d ' ')
  jwt_status="JWT secret unset"
  if [[ -n "${APP_SECURITY_JWT_SECRET:-}" ]]; then
    jwt_status="JWT secret set (${#APP_SECURITY_JWT_SECRET} chars)"
  fi
  echo "→ loaded .env — ${env_lines} lines, ${jwt_status}"
fi

# Secret JWT par défaut si --secure et pas défini
if [[ "${PROFILES}" == *secure* ]]; then
  export APP_SECURITY_OAUTH2_ENABLED=true
  if [[ -z "${APP_SECURITY_JWT_SECRET:-}" ]]; then
    export APP_SECURITY_JWT_SECRET="dev-test-secret-256bits-mini-aaaaaaaaaaaa"
    echo "⚠️  APP_SECURITY_JWT_SECRET non défini — secret de test (NE PAS faire en prod)"
  fi
  echo "→ OAuth2 Resource Server enabled (HS256 secret length = ${#APP_SECURITY_JWT_SECRET})"
fi

echo
echo "═══════════════════════════════════════════════════════════════"
echo "→ Mode      : ${MODE}"
echo "→ Profiles  : ${PROFILES}"
echo "→ Port      : ${PORT}"
echo "→ Swagger   : http://localhost:${PORT}/swagger-ui.html"
echo "→ Actuator  : http://localhost:${PORT}/actuator/health"
echo "═══════════════════════════════════════════════════════════════"
echo

exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="${PROFILES}"
