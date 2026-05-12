#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# es-start.sh — Démarrer Elasticsearch 9.4.0 en local (sprint ES, mai 2026)
# ══════════════════════════════════════════════════════════════════════════
# Historique : initialement ES 7.17.4 via brew tap. Sprint upgrade vers
# ES 9.4.0 quand Spring Boot 4.0.6 a embarqué elasticsearch-java 9.2.8
# (incompatible avec serveur 7.17 → health probe DOWN, search KO).
#
# ES 9.4.0 :
#   - JDK bundled (pas besoin de ES_JAVA_HOME)
#   - xpack.security désactivé pour dev local (cf elasticsearch.yml dans
#     ${ES_HOME}/config/, monté avec discovery.type=single-node)
#   - Pas de TLS, pas d'auth (NE PAS faire en prod)
#
# Installation manuelle (download ~625 MB, hors brew car la formule tape
# l'ancienne 7.17.4) :
#   mkdir -p ~/elasticsearch-9 && cd ~/elasticsearch-9
#   curl -LO https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-9.4.0-darwin-aarch64.tar.gz
#   tar xzf elasticsearch-9.4.0-darwin-aarch64.tar.gz
#   # Copier la config OneClick (single-node + xpack.security disabled) :
#   #   cf $REPO/scripts/es-config-9.yml — à appliquer une fois
#
# Usage :
#   ./scripts/es-start.sh             # foreground
#   ./scripts/es-start.sh --daemon    # background, log dans /tmp/es9-oneclick.log
#   curl http://localhost:9200/       # vérifier (must return version 9.4.0)
# ══════════════════════════════════════════════════════════════════════════

set -e

ES_HOME="${ES_HOME:-$HOME/elasticsearch-9/elasticsearch-9.4.0}"
LOG_FILE="/tmp/es9-oneclick.log"

if [[ ! -x "$ES_HOME/bin/elasticsearch" ]]; then
  echo "❌ Elasticsearch 9.4 absent dans $ES_HOME"
  echo
  echo "Installation manuelle :"
  echo "  mkdir -p ~/elasticsearch-9 && cd ~/elasticsearch-9"
  echo "  curl -LO https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-9.4.0-darwin-aarch64.tar.gz"
  echo "  tar xzf elasticsearch-9.4.0-darwin-aarch64.tar.gz"
  echo
  echo "Puis copier la config OneClick :"
  echo "  cp scripts/es-config-9.yml ~/elasticsearch-9/elasticsearch-9.4.0/config/elasticsearch.yml"
  exit 1
fi

# Si déjà up, ne pas relancer
if nc -z localhost 9200 2>/dev/null; then
  VERSION=$(curl -s http://localhost:9200/ | python3 -c "import json,sys;print(json.load(sys.stdin)['version']['number'])" 2>/dev/null || echo "?")
  echo "✅ Elasticsearch déjà UP sur :9200 (version $VERSION)"
  if [[ "$VERSION" != 9.* ]]; then
    echo "⚠️  Version $VERSION détectée — sortir ce process et lancer ES 9.4 :"
    echo "    pkill -f elasticsearch && sleep 3 && ./scripts/es-start.sh --daemon"
  fi
  exit 0
fi

echo "🚀 Démarrage Elasticsearch 9.4.0 (JDK bundled, xpack.security=false)..."

if [[ "${1:-}" == "--daemon" ]]; then
  nohup "$ES_HOME/bin/elasticsearch" > "$LOG_FILE" 2>&1 &
  echo "   PID: $!"
  echo "   Log: $LOG_FILE"
  echo "   Attendre 30-60s puis : curl http://localhost:9200/"
else
  exec "$ES_HOME/bin/elasticsearch"
fi
