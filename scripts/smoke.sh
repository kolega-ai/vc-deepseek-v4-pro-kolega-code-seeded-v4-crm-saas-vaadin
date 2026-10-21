#!/usr/bin/env bash
# End-to-end smoke check for the CrmOps multi-tier setup (run after `docker compose up -d --build`).
# Exercises: health, decision login/authorize/scope, decision->core-mock scope tokens,
# channel API-key surface, webhook delivery chain and batch jobs.
set -euo pipefail

BASE_DECISION=http://localhost:${DECISION_PORT:-8081}
BASE_CORE=http://localhost:${CORE_PORT:-8082}
BASE_CHANNEL=http://localhost:${CHANNEL_PORT:-8080}
BASE_BATCH=http://localhost:${BATCH_PORT:-8083}
SERVICE_TOKEN=local-channel-service-token
CORE_KEY=local-core-service-key

say() { printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }
fail() { printf '\033[1;31mFAILED: %s\033[0m\n' "$*"; exit 1; }

say "1. Health checks"
for url in "$BASE_DECISION/actuator/health" "$BASE_CORE/actuator/health" "$BASE_CHANNEL/actuator/health" "$BASE_BATCH/actuator/health"; do
  curl -fs "$url" > /dev/null || fail "unhealthy: $url"
done
echo "all tiers healthy"

say "2. Decision: login, authorize, scope"
TOKEN=$(curl -fs -X POST "$BASE_DECISION/api/auth/login" \
  -H "Content-Type: application/json" -H "X-Service-Token: $SERVICE_TOKEN" \
  -d '{"username":"sarah.chan","password":"Manager!Crmops2026"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
[ -n "$TOKEN" ] || fail "decision login"

ALLOWED=$(curl -fs -X POST "$BASE_DECISION/api/authorize" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"action":"org.read","orgId":1}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["allowed"])')
[ "$ALLOWED" = "True" ] || fail "sarah should access org 1"
DENIED=$(curl -fs -X POST "$BASE_DECISION/api/authorize" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"action":"user.admin","orgId":1}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["allowed"])')
[ "$DENIED" = "False" ] || fail "account manager must not carry user.admin"
SCOPE=$(curl -fs -X POST "$BASE_DECISION/api/authorize/scope" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"action":"org.read"}' | python3 -c 'import sys,json;print(",".join(map(str,json.load(sys.stdin)["allowedOrgIds"])))')
echo "sarah scope: [$SCOPE]"

say "3. Decision-minted scope token -> vendor core"
SCOPE_TOKEN=$(curl -fs -X POST "$BASE_DECISION/api/tokens/scope" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"orgIds":[1],"scope":"invoices.read"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -fs "$BASE_CORE/api/orgs/1/invoices" -H "X-Core-Service-Key: $CORE_KEY" \
  -H "Authorization: Bearer $SCOPE_TOKEN" | python3 -c 'import sys,json;d=json.load(sys.stdin);assert d["orgId"]==1 and len(d["invoices"])>=2;print("invoices for org 1:",len(d["invoices"]))'
# scope violation must be rejected with the stable vendor-core error code
CODE=$(curl -s "$BASE_CORE/api/orgs/2/invoices" -H "X-Core-Service-Key: $CORE_KEY" \
  -H "Authorization: Bearer $SCOPE_TOKEN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
[ "$CODE" = "core-1003" ] || fail "cross-org read must be rejected with core-1003"

say "4. Channel API-key surface (org-scoped)"
API_KEY=$(docker compose logs channel 2>/dev/null | grep -oE 'crm_live_[A-Za-z0-9_-]+' | head -1)
[ -n "$API_KEY" ] || fail "seeded api key not found in channel logs"
ORG=$(curl -fs "$BASE_CHANNEL/api/v1/org" -H "X-Api-Key: $API_KEY")
echo "$ORG" | python3 -c 'import sys,json;d=json.load(sys.stdin);assert d["name"]=="Nimbus Cloud",d;print("org profile:",d["name"],d["plan"])'
curl -fs "$BASE_CHANNEL/api/v1/documents" -H "X-Api-Key: $API_KEY" | python3 -c 'import sys,json;d=json.load(sys.stdin);assert all(x["category"] for x in d);print("customer-visible documents:",len(d))'

say "5. Escalation via API key -> webhook delivery chain"
ESC=$(curl -fs -X POST "$BASE_CHANNEL/api/v1/escalations" -H "X-Api-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"subject":"Smoke test escalation","description":"Created by the smoke script.","severity":"SEV3"}')
echo "$ESC"
curl -fs -X POST "$BASE_BATCH/api/jobs/webhooks/run" -H "X-Batch-Admin-Token: local-batch-admin-token" > /dev/null
# poll the customer sink for up to 90s (deliveries are asynchronous)
for i in $(seq 1 18); do
  curl -fs "$BASE_CORE/mock/webhook-sink/nimbus" > /tmp/sink.json
  if python3 -c 'import json;d=json.load(open("/tmp/sink.json"));assert any(x.get("eventType")=="escalation.created" for x in d)' 2>/dev/null; then
    echo "webhook received by customer sink: escalation.created"
    break
  fi
  sleep 5
done
python3 -c 'import json;d=json.load(open("/tmp/sink.json"));assert any(x.get("eventType")=="escalation.created" for x in d), d;print("sink verified")'

say "6. Batch jobs"
curl -fs -X POST "$BASE_BATCH/api/jobs/outbox/run" -H "X-Batch-Admin-Token: local-batch-admin-token" > /dev/null && echo "outbox job ok"
curl -fs -X POST "$BASE_BATCH/api/jobs/escalation-sla/run" -H "X-Batch-Admin-Token: local-batch-admin-token" > /dev/null && echo "sla job ok"

say "All smoke checks passed ✔"
