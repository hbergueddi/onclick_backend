#!/usr/bin/env bash
# run.sh — Lance Spring Boot avec Java 26 sans toucher au $JAVA_HOME global.
#
# Utile sur les machines où JAVA_HOME pointe ailleurs (ex: Android Studio JBR
# en Java 21 sur les Mac avec Capacitor).
#
# Usage:
#   ./run.sh                       # mode dev (oauth2 disabled)
#   ./run.sh --oauth2              # active OAuth2 RS avec un secret de test
#   APP_SECURITY_JWT_SECRET=xxx ./run.sh --oauth2   # active avec votre secret

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
echo

if [[ "${1:-}" == "--oauth2" ]]; then
  export APP_SECURITY_OAUTH2_ENABLED=true
  export APP_SECURITY_JWT_SECRET="${APP_SECURITY_JWT_SECRET:-dev-test-secret-256bits-mini-aaaaaaaaaaaa}"
  echo "→ OAuth2 Resource Server enabled (HS256 secret length = ${#APP_SECURITY_JWT_SECRET})"
  echo
fi

exec ./mvnw spring-boot:run
