#!/usr/bin/env bash
# ports-status.sh — état des ports OneClick (apps + infra)
#
# Usage :
#   ./scripts/ports-status.sh           # affiche l'état
#   ./scripts/ports-status.sh --clean   # kill processus apps zombies (3000, 8080-8087)
#                                       (préserve infra : Postgres, Redis, Kafka, ES, MinIO)
# ══════════════════════════════════════════════════════════════════════════
# Compatible bash 3.2 (macOS default) — pas d'arrays associatifs.

check_port() {
  local port=$1
  local name=$2
  local pid
  pid=$(lsof -ti ":$port" 2>/dev/null)
  if [[ -n "$pid" ]]; then
    printf "  ✅ %-5s %-30s (PID %s)\n" "$port" "$name" "$pid"
  else
    printf "  ⬜ %-5s %-30s (libre)\n" "$port" "$name"
  fi
}

print_apps() {
  echo "─── Apps (Spring + Nuxt) ──────────────────────────────────"
  check_port 3000 "Nuxt frontend"
  check_port 8080 "oneclick-gateway"
  check_port 8083 "oneclick-core"
  check_port 8084 "oneclick-notification-service"
  check_port 8085 "oneclick-loyalty-service"
  check_port 8086 "oneclick-payment-service"
  check_port 8087 "oneclick-search-service"
  echo ""
}

print_infra() {
  echo "─── Infrastructure (NE PAS KILLER) ────────────────────────"
  check_port 5432 "Postgres"
  check_port 6379 "Redis"
  check_port 9000 "MinIO API"
  check_port 9001 "MinIO Console"
  check_port 9092 "Kafka"
  check_port 9200 "Elasticsearch"
  echo ""
}

clean_apps() {
  echo "🧹 Cleanup zombies apps (ports 3000, 8080-8087)..."
  for port in 3000 8080 8083 8084 8085 8086 8087; do
    pid=$(lsof -ti ":$port" 2>/dev/null)
    if [[ -n "$pid" ]]; then
      echo "   kill $pid (port $port)"
      kill "$pid" 2>/dev/null
    fi
  done
  sleep 2
  echo "   ✅ Done"
  echo ""
}

# ─── Main ──────────────────────────────────────────────────────────
print_apps
print_infra

if [[ "${1:-}" == "--clean" ]]; then
  clean_apps
  echo "═══ État après cleanup ═══"
  print_apps
fi
