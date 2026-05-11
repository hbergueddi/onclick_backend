#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# es-start.sh — Démarrer Elasticsearch 7.17 en local
# ══════════════════════════════════════════════════════════════════════════
# Pourquoi un script custom plutôt que `brew services` ?
# - ES 7.17 nécessite Java 11-17 (impossible avec JDK 25/26 par défaut)
# - `brew services` ne préserve pas `ES_JAVA_HOME` correctement
# - On a désactivé xpack.ml (libs natives incompatibles macOS arm64)
#
# Usage :
#   ./scripts/es-start.sh             # foreground
#   ./scripts/es-start.sh --daemon    # background, log dans /tmp/es-oneclick.log
#   curl http://localhost:9200/       # vérifier
# ══════════════════════════════════════════════════════════════════════════

set -e

ES_JAVA_HOME="/opt/homebrew/opt/openjdk@17"
ES_BIN="/opt/homebrew/opt/elasticsearch-full/bin/elasticsearch"
LOG_FILE="/tmp/es-oneclick.log"

if [[ ! -d "$ES_JAVA_HOME" ]]; then
  echo "❌ OpenJDK 17 absent. Installer : brew install openjdk@17"
  exit 1
fi

if [[ ! -x "$ES_BIN" ]]; then
  echo "❌ Elasticsearch absent. Installer : brew tap elastic/tap && brew install elastic/tap/elasticsearch-full"
  exit 1
fi

# Si déjà up, ne pas relancer
if nc -z localhost 9200 2>/dev/null; then
  echo "✅ Elasticsearch déjà UP sur :9200"
  curl -s http://localhost:9200/ | python3 -c "import json,sys;d=json.load(sys.stdin);print(f\"   version {d['version']['number']}, cluster {d['cluster_name']}\")"
  exit 0
fi

echo "🚀 Démarrage Elasticsearch 7.17.4 (JDK 17, xpack.ml=false)..."
export ES_JAVA_HOME

if [[ "${1:-}" == "--daemon" ]]; then
  nohup "$ES_BIN" > "$LOG_FILE" 2>&1 &
  echo "   PID: $!"
  echo "   Log: $LOG_FILE"
  echo "   Attendre 30-60s puis : curl http://localhost:9200/"
else
  exec "$ES_BIN"
fi
