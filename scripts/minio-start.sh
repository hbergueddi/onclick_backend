#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
# minio-start.sh — Démarrer MinIO en local
# ══════════════════════════════════════════════════════════════════════════
# Usage :
#   ./scripts/minio-start.sh             # foreground
#   ./scripts/minio-start.sh --daemon    # background, log /tmp/minio.log
#
# Endpoints :
#   API     : http://localhost:9000  (S3-compatible)
#   Console : http://localhost:9001  (UI web)
#   Creds   : minioadmin / minioadmin (à changer en prod)
#   Bucket  : oneclick-media (download anonymous)
# ══════════════════════════════════════════════════════════════════════════

set -e

DATA_DIR="${HOME}/minio-data"
LOG_FILE="/tmp/minio.log"

if ! command -v minio >/dev/null 2>&1; then
  echo "❌ MinIO absent. Installer : brew install minio/stable/minio minio/stable/mc"
  exit 1
fi

mkdir -p "$DATA_DIR"

if nc -z localhost 9000 2>/dev/null; then
  echo "✅ MinIO déjà UP sur :9000 (console :9001)"
  exit 0
fi

echo "🚀 Démarrage MinIO (data: $DATA_DIR)..."

if [[ "${1:-}" == "--daemon" ]]; then
  nohup minio server "$DATA_DIR" --address ":9000" --console-address ":9001" > "$LOG_FILE" 2>&1 &
  echo "   PID: $!"
  echo "   Log: $LOG_FILE"
  sleep 3
  nc -z localhost 9000 && echo "✅ MinIO API :9000 UP" || echo "❌ MinIO startup failed"
else
  exec minio server "$DATA_DIR" --address ":9000" --console-address ":9001"
fi
