#!/usr/bin/env bash
# End-to-end smoke test through the gateway. Usage: scripts/demo.sh [payment-token]
#   payment-token: tok_visa_4242 (default, succeeds) | tok_decline (fails -> stock released) | tok_timeout (idempotent retry)
set -euo pipefail
GW=${GATEWAY:-http://localhost:8080}
TOKEN=$(python3 "$(dirname "$0")/dev_token.py")
PAY_TOKEN=${1:-tok_visa_4242}
KEY=$(python3 -c 'import uuid;print(uuid.uuid4())')

BODY=$(cat <<JSON
{"items":[{"sku":"SKU-IPHONE-15-128","quantity":1},{"sku":"SKU-CASE-BLK","quantity":2}],
 "shippingAddressId":"a1b2c3d4-0000-4000-8000-000000000001",
 "payment":{"method":"CARD","paymentMethodToken":"$PAY_TOKEN","currency":"INR"}}
JSON
)
echo "== stock before"; curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/inventory/SKU-IPHONE-15-128; echo

echo "== checkout (Idempotency-Key $KEY)"
RESP=$(curl -s -X POST $GW/api/v1/checkout -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$BODY")
echo "$RESP"
ORDER_ID=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["orderId"])')

echo "== replay same key (expect 200 + same orderId)"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST $GW/api/v1/checkout -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$BODY"

echo "== polling order"
for i in $(seq 1 15); do
  S=$(curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/orders/$ORDER_ID)
  echo "$S"
  echo "$S" | grep -qE '"status":"(CONFIRMED|CANCELLED|REJECTED)"' && break
  sleep 2
done
echo "== stock after"; curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/inventory/SKU-IPHONE-15-128; echo
echo "== payment"; curl -s -H "Authorization: Bearer $TOKEN" $GW/api/v1/payments/$ORDER_ID; echo
