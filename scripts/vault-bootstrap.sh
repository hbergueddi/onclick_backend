#!/bin/sh
# ════════════════════════════════════════════════════════════════════
# Bootstrap des secrets Vault — Sprint I.8
# ════════════════════════════════════════════════════════════════════
#
# Spring Cloud Vault avec `default-context: oneclick` lit le KV path
# `secret/data/oneclick` (un seul path, flat keys). On stocke donc
# TOUS les secrets dans ce path unique avec des préfixes hiérarchiques :
#
#   secret/oneclick :
#     db.username, db.password, db.url
#     jwt.secret, jwt.issuer
#     ai.groq.api-key, ai.groq.endpoint, ai.groq.model
#     notification.fcm.project-id, notification.fcm.service-account-json
#     ocr.space.api-key, ocr.space.endpoint
#     email.resend.api-key, email.from.default
#     google.places.api-key
#     monitoring.sentry.dsn (préfixé pour éviter conflit auto-config)
#
# Variables attendues :
#   VAULT_ADDR    (ex: http://vault:8200)
#   VAULT_TOKEN   (ex: oneclick-dev-root)
# ════════════════════════════════════════════════════════════════════

set -e

echo "[vault-bootstrap] Vault $VAULT_ADDR — token starts $(echo $VAULT_TOKEN | head -c 8)..."

# 1. Attendre Vault prêt
i=0
until vault status > /dev/null 2>&1; do
  i=$((i+1))
  if [ $i -gt 30 ]; then
    echo "[vault-bootstrap] Vault not ready after 60s — abort"
    exit 1
  fi
  sleep 2
done
echo "[vault-bootstrap] Vault is ready ✓"

# 2. Activer KV v2 sur /secret/* (déjà actif en dev)
vault secrets list -format=json 2>/dev/null | grep -q '"secret/"' && \
  echo "[vault-bootstrap] kv-v2 engine déjà actif sur /secret/" || {
    vault secrets enable -version=2 -path=secret kv || true
  }

# 3. Cleanup ancien layout subpath (best-effort)
for p in db jwt ai push ocr email places sentry wallet rate-limit; do
  vault kv metadata delete "secret/oneclick/$p" > /dev/null 2>&1 || true
done

# 4. Provision FLAT sur /secret/oneclick avec préfixes hiérarchiques
echo "[vault-bootstrap] Provisionning /secret/oneclick (flat keys)..."

vault kv put secret/oneclick \
  db.username="hh" \
  db.password="oneclick_dev_pwd" \
  db.url="jdbc:postgresql://localhost:5432/oneclick_enterprise" \
  jwt.secret="vault-managed-jwt-secret-min-256-bits-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
  jwt.issuer="oneclick-enterprise" \
  jwt.ttl-seconds="3600" \
  jwt.refresh-ttl-days="30" \
  ai.groq.api-key="" \
  ai.groq.endpoint="https://api.groq.com/openai/v1/chat/completions" \
  ai.groq.model="llama-3.3-70b-versatile" \
  notification.fcm.project-id="oneclick-129bb" \
  notification.fcm.service-account-json="" \
  ocr.space.api-key="" \
  ocr.space.endpoint="https://api.ocr.space/parse/imageurl" \
  email.resend.api-key="" \
  email.from.default="OneClick <noreply@app-oneclick.net>" \
  google.places.api-key="" \
  monitoring.sentry.dsn="" \
  monitoring.sentry.environment="dev" \
  monitoring.sentry.traces-sample-rate="0.2" \
  wallet.apple.pass-type-id="pass.ma.oneclick.loyalty" \
  wallet.apple.team-id="PX5PTJXLPX" \
  wallet.apple.cert-base64="" \
  wallet.google.issuer-id="0000000000000000000" \
  wallet.google.service-account-json="" \
  rate-limit.enabled="false" \
  rate-limit.global-tokens-per-minute="100" \
  rate-limit.login-tokens-per-minute="10"

echo ""
echo "[vault-bootstrap] ✅ Secret /oneclick provisionné (flat structure)"
echo ""
echo "[vault-bootstrap] Sample (db.* keys) :"
vault kv get -field=db.username secret/oneclick
vault kv get -field=jwt.issuer secret/oneclick
echo ""
echo "[vault-bootstrap] Pour lire tout :"
echo "  docker exec -it oneclick_vault vault kv get secret/oneclick"
echo ""
echo "[vault-bootstrap] Done."
