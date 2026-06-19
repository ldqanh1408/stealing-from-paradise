#!/bin/bash
# ==============================================================================
# E2E Test Script: Payout Flow A-Z (Commission & Payout)
# ==============================================================================
# This script automates:
#   1. Depositing available USD balance on the seller's Stripe Connected Account
#   2. Creating an order & submitting checkout
#   3. Triggering payment_intent.succeeded webhook
#   4. Fulfilling the order (Ship & Deliver)
#   5. Setting the transfer eligibility to past timestamp in DB
#   6. Temporarily configuring payment-service for USD payouts
#   7. Verifying the payout scheduler processes it successfully (PAID_OUT)
#   8. Restoring original configurations
# ==============================================================================

set -e

SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/../../.." && pwd)"

# Parse specific variables from .env to avoid syntax issues
if [ -f "$PROJECT_ROOT/.env" ]; then
    STRIPE_SECRET_KEY=$(grep -E '^STRIPE_SECRET_KEY=' "$PROJECT_ROOT/.env" | cut -d'=' -f2-)
    PORT_GW=$(grep -E '^PORT_GATEWAY=' "$PROJECT_ROOT/.env" | cut -d'=' -f2-)
    if [ -n "$PORT_GW" ]; then
        GATEWAY="http://localhost:$PORT_GW"
    fi
fi

GATEWAY="${GATEWAY:-http://localhost:8080}"
STRIPE_SECRET_KEY="${STRIPE_SECRET_KEY:-sk_test_placeholder}"
CONNECTED_ACCOUNT="acct_1Tjij4Cz262ctOlb"
VARIANT="90000000-0000-4000-9001-000000000302" # FE-SKU-PHONE-BLUE (seller 900002)
CUST_ID=6

echo "=============================================================="
echo " E2E PAYOUT FLOW TEST (A-Z)"
echo "=============================================================="
echo " Gateway:           $GATEWAY"
echo " Connected Account: $CONNECTED_ACCOUNT"
echo " Variant ID:        $VARIANT"
echo "=============================================================="

# Helper function: API call parser
login() {
  curl -s -X POST "$GATEWAY/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"credential\":\"$1\",\"password\":\"dev123\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken') or '')"
}

poll_payment_status() {
  url="$1"; tok="$2"; expected="$3"; label="$4"
  i=0
  while [ $i -lt 30 ]; do
    val=$(curl -s "$url" -H "Authorization: Bearer $tok" \
      | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('status') or '')")
    if [ "$val" = "$expected" ]; then
      echo "  [OK] $label Status = $val"
      return 0
    fi
    i=$((i+1)); sleep 2
  done
  echo "  [TIMEOUT] $label expected='$expected' last='$val'"
  return 1
}

# Step 1: Prep Stripe Connected Account Balance
echo "[Step 1] Checking Stripe Connected Account Balance..."
BAL_INFO=$(curl -s https://api.stripe.com/v1/balance -u "$STRIPE_SECRET_KEY:" -H "Stripe-Account: $CONNECTED_ACCOUNT")
AVAIL_BAL=$(echo "$BAL_INFO" | python3 -c "import sys,json;b=json.load(sys.stdin).get('available',[]);print(b[0]['amount'] if b else 0)")

echo "[+] Current Available Balance: $AVAIL_BAL cents ($(($AVAIL_BAL/100)) USD)"

if [ "$AVAIL_BAL" -lt 150000 ]; then
  echo "[+] Available balance is low. Injecting $5,000 USD via tok_bypassPending..."
  curl -s https://api.stripe.com/v1/charges \
    -u "$STRIPE_SECRET_KEY:" \
    -H "Stripe-Account: $CONNECTED_ACCOUNT" \
    -d amount=500000 \
    -d currency=usd \
    -d source=tok_bypassPending > /dev/null
  echo "[+] Balance successfully updated!"
fi

# Step 2: Clear Cart & Submit Checkout
echo "[Step 2] Authenticating Buyer & Submitting Checkout..."
BUYER_TOKEN=$(login minhhoa)
if [ -z "$BUYER_TOKEN" ]; then
  echo "[FAIL] Failed to login buyer 'minhhoa'"
  exit 1
fi

echo "[+] Clearing Cart..."
curl -s -X DELETE "$GATEWAY/api/v1/cart" -H "Authorization: Bearer $BUYER_TOKEN" >/dev/null

echo "[+] Adding item to cart..."
curl -s -X POST "$GATEWAY/api/v1/cart/items" -H "Authorization: Bearer $BUYER_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"variantId\":\"$VARIANT\",\"quantity\":1}" >/dev/null

echo "[+] Creating checkout preview..."
PT=$(curl -s -X POST "$GATEWAY/api/v1/cart/checkout/preview" -H "Authorization: Bearer $BUYER_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"itemIds\":[\"$CUST_ID:$VARIANT\"]}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('previewToken') or '')")
if [ -z "$PT" ]; then
  echo "[FAIL] Failed to acquire preview token"
  exit 1
fi

echo "[+] Getting shipping address..."
AID=$(curl -s "$GATEWAY/api/v1/users/me/addresses" -H "Authorization: Bearer $BUYER_TOKEN" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',[])[0].get('address_id') or '')")
if [ -z "$AID" ]; then
  echo "[FAIL] Failed to acquire address ID"
  exit 1
fi

# Track parent order ID
PRE_MAX_ORDER=$(curl -s "$GATEWAY/api/v1/orders?page=0&size=100" -H "Authorization: Bearer $BUYER_TOKEN" \
  | python3 -c "import sys,json;c=json.load(sys.stdin).get('data',{}).get('content',[]);print(max([o.get('parentOrderId') or 0 for o in c]+[0]))")

echo "[+] Submitting checkout..."
curl -s -X POST "$GATEWAY/api/v1/cart/checkout/submit" -H "Authorization: Bearer $BUYER_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"previewToken\":\"$PT\",\"addressId\":$AID}" >/dev/null

PID=""
i=0
while [ $i -lt 30 ]; do
  PID=$(curl -s "$GATEWAY/api/v1/orders?page=0&size=100" -H "Authorization: Bearer $BUYER_TOKEN" \
    | PRE=$PRE_MAX_ORDER python3 -c "import sys,json,os;m=int(os.environ['PRE']);c=json.load(sys.stdin).get('data',{}).get('content',[]);n=[o['parentOrderId'] for o in c if (o.get('parentOrderId') or 0)>m];print(n[0] if n else '')")
  if [ -n "$PID" ]; then
    break
  fi
  i=$((i+1)); sleep 1
done

if [ -z "$PID" ]; then
  echo "[FAIL] Checkout submission failed to generate a new Parent Order"
  exit 1
fi
echo "[+] Parent Order ID generated: $PID"

# Step 3: Pay Order (Forge payment_intent.succeeded)
echo "[Step 3] Forging Payment Webhook..."
python3 "$SCRIPTS_DIR/forge.py" pi payment_intent.succeeded "$PID"

poll_payment_status "$GATEWAY/api/v1/payments/parent-order/$PID" "$BUYER_TOKEN" "SUCCESS" "Transaction Status"

# Step 4: Fulfillment (Seller Ships Order)
echo "[Step 4] Fulfilling the Order (Fulfillment)..."
# Retrieve Sub-Order ID and Seller ID
SUB_ORDER=$(curl -s "$GATEWAY/api/v1/orders/parent/$PID" -H "Authorization: Bearer $BUYER_TOKEN" \
  | python3 -c "import sys,json;o=json.load(sys.stdin)['data']['orders'][0];print(o['orderId'],o['sellerId'])")
ORDER_ID=$(echo $SUB_ORDER | awk '{print $1}')
SID=$(echo $SUB_ORDER | awk '{print $2}')

echo "[+] Sub-Order ID: $ORDER_ID, Seller ID: $SID"

# Login as seller (techworld)
SELLER_TOKEN=$(login techworld)
if [ -z "$SELLER_TOKEN" ]; then
  echo "[FAIL] Failed to login seller 'techworld'"
  exit 1
fi

echo "[+] Updating tracking number (Shipping)..."
curl -s -X PUT "$GATEWAY/api/v1/orders/$ORDER_ID/tracking" -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"trackingNumber\":\"E2E-PAYOUT-$ORDER_ID\"}" >/dev/null

poll_payment_status "$GATEWAY/api/v1/orders/$ORDER_ID" "$BUYER_TOKEN" "SHIPPING" "Order Shipping Status"

# Step 5: Confirm Delivery
echo "[Step 5] Confirming Order Delivery..."
curl -s -X POST "$GATEWAY/api/v1/orders/$ORDER_ID/confirm-received" -H "Authorization: Bearer $BUYER_TOKEN" \
  -H 'Content-Type: application/json' -d '{}' >/dev/null

poll_payment_status "$GATEWAY/api/v1/orders/$ORDER_ID" "$BUYER_TOKEN" "DELIVERED" "Order Final Status"

# Step 6: Make Transfer Eligible in DB
echo "[Step 6] Updating DB to make the Seller Transfer eligible for immediate payout..."
docker exec -i fs-postgres psql -U postgres -d flashsale_platform -c \
  "UPDATE payment.seller_transfers SET status = 'RETURN_WINDOW', payout_eligible_at = now() - interval '1 hour' WHERE parent_order_id = $PID;" > /dev/null

echo "[+] DB update complete."

# Step 7: Configure USD Payout & Recreate payment-service
echo "[Step 7] Temporarily configuring payment-service for USD Payouts..."
cp "$PROJECT_ROOT/.env" "$PROJECT_ROOT/.env.backup"
cp "$PROJECT_ROOT/docker-compose.yml" "$PROJECT_ROOT/docker-compose.yml.backup"

# Add temporary configs to .env
echo "PAYOUT_TRANSFER_CURRENCY=usd" >> "$PROJECT_ROOT/.env"
echo "PAYOUT_TRANSFER_AMOUNT_SCALE=0.0037984" >> "$PROJECT_ROOT/.env"

# Add variables to docker-compose.yml
python3 -c "
with open('$PROJECT_ROOT/docker-compose.yml', 'r') as f:
    c = f.read()
target = '- JVM_OPTS=\${JVM_OPTS_PAYMENT}'
replacement = '- JVM_OPTS=\${JVM_OPTS_PAYMENT}\n      - PAYOUT_TRANSFER_CURRENCY=\${PAYOUT_TRANSFER_CURRENCY}\n      - PAYOUT_TRANSFER_AMOUNT_SCALE=\${PAYOUT_TRANSFER_AMOUNT_SCALE}'
c_new = c.replace(target, replacement)
with open('$PROJECT_ROOT/docker-compose.yml', 'w') as f:
    f.write(c_new)
"

echo "[+] Recreating payment-service container..."
docker compose -f "$PROJECT_ROOT/docker-compose.yml" up -d payment-service > /dev/null

echo "[+] Waiting for payment-service container to be healthy..."
i=0
while [ $i -lt 40 ]; do
  HEALTH=$(docker inspect --format='{{json .State.Health.Status}}' fs-payment 2>/dev/null || echo "\"starting\"")
  if [ "$HEALTH" = "\"healthy\"" ]; then
    echo "  [OK] payment-service is healthy!"
    break
  fi
  i=$((i+1)); sleep 3
done

# Step 8: Verify Payout Success
echo "[Step 8] Monitoring Payout Scheduler..."
i=0
PAID_OUT="false"
while [ $i -lt 30 ]; do
  STATUS_INFO=$(docker exec -i fs-postgres psql -U postgres -d flashsale_platform -t -A -c \
    "SELECT status, stripe_payout_id FROM payment.seller_transfers WHERE parent_order_id = $PID;")
  
  ST_STATUS=$(echo "$STATUS_INFO" | cut -d'|' -f1)
  ST_PAYOUT_ID=$(echo "$STATUS_INFO" | cut -d'|' -f2)

  if [ "$ST_STATUS" = "PAID_OUT" ] && [ -n "$ST_PAYOUT_ID" ]; then
    echo "=============================================================="
    echo " SUCCESS: Payout executed successfully!"
    echo " Parent Order ID:  $PID"
    echo " Transfer Status:  $ST_STATUS"
    echo " Stripe Payout ID: $ST_PAYOUT_ID"
    echo "=============================================================="
    PAID_OUT="true"
    break
  fi
  i=$((i+1)); sleep 2
done

if [ "$PAID_OUT" != "true" ]; then
  echo "[FAIL] Payout Scheduler did not process the transfer to PAID_OUT. Check logs:"
  docker logs --tail 50 fs-payment
  exit 1
fi

# Step 9: Restore Original Config
echo "[Step 9] Restoring original configuration..."
mv "$PROJECT_ROOT/.env.backup" "$PROJECT_ROOT/.env"
mv "$PROJECT_ROOT/docker-compose.yml.backup" "$PROJECT_ROOT/docker-compose.yml"

echo "[+] Rebuilding/starting original payment-service container..."
docker compose -f "$PROJECT_ROOT/docker-compose.yml" up -d payment-service > /dev/null

echo "[+] Re-verifying original container health..."
i=0
while [ $i -lt 40 ]; do
  HEALTH=$(docker inspect --format='{{json .State.Health.Status}}' fs-payment 2>/dev/null || echo "\"starting\"")
  if [ "$HEALTH" = "\"healthy\"" ]; then
    echo "  [OK] payment-service is healthy under original configuration!"
    break
  fi
  i=$((i+1)); sleep 3
done

echo "=============================================================="
echo " E2E PAYOUT FLOW TEST COMPLETE & SUCCESSFUL"
echo "=============================================================="
