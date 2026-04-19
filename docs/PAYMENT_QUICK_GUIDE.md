# 💳 PAYMENT SETUP & TEST QUICK GUIDE

**Ngày**: 2026-04-19  
**Mục Đích**: Quick reference cho payment setup & testing

---

## 5-Phút Setup

### Bước 1: Stripe Account (1 phút)

```bash
# Truy cập: https://dashboard.stripe.com/register
# Tạo tài khoản FREE → Verify email → Login

# Lấy keys:
# - Developers → API Keys
# - Copy: Publishable Key (pk_test_...) + Secret Key (sk_test_...)
```

### Bước 2: Stripe CLI & Webhook (2 phút)

```bash
# Install
brew install stripe/stripe-cli/stripe  # macOS
choco install stripe-cli               # Windows

# Login
stripe login
# -> Authorize trong browser

# Forward webhooks (Terminal riêng - GIỮ LUN CHẠY!)
stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks
# -> Lưu webhook secret: whsec_test_xxxxx
```

### Bước 3: Setup .env (1 phút)

```bash
# Mở .env
STRIPE_SECRET_KEY=sk_test_xxxxx
STRIPE_WEBHOOK_SECRET=whsec_test_xxxxx
STRIPE_PLATFORM_FEE_PERCENTAGE=5.0
```

### Bước 4: Restart Services (1 phút)

```bash
docker-compose down
docker-compose up -d
# Chờ 3-5 phút
```

---

## Test Buyer Payment (5 phút)

### Option 1: Browser (Frontend)

```bash
# 1. http://localhost:3000
# 2. Register buyer
# 3. Browse product
# 4. Add to cart
# 5. Checkout
# 6. Payment page
# 7. Card: 4242 4242 4242 4242
# 8. Exp: 12/26, CVC: 123
# 9. Click Pay
# 10. Wait 3-5 sec
# 11. See "Payment Successful" ✅
```

### Option 2: API (Backend)

```bash
#!/bin/bash
# save as: test_payment.sh && chmod +x test_payment.sh

# Create seller & product
SELLER=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"seller_'$(date +%s)'","email":"seller_'$(date +%s)'@test.local","password":"Password123!","phone":"0901234567","role":"SELLER"}')
SELLER_TOKEN=$(echo $SELLER | jq -r '.access_token')

PRODUCT=$(curl -s -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sku_code":"TEST-'$(date +%s)'","name":"Test","price":100000,"stock":10,"category_id":1}')
PRODUCT_ID=$(echo $PRODUCT | jq '.id')

# Create buyer
BUYER=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"buyer_'$(date +%s)'","email":"buyer_'$(date +%s)'@test.local","password":"Password123!","phone":"0901234568"}')
BUYER_TOKEN=$(echo $BUYER | jq -r '.access_token')

# Add to cart
curl -s -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"product_id":'$PRODUCT_ID',"quantity":1}' > /dev/null

# Create address
ADDRESS=$(curl -s -X POST http://localhost:8080/api/v1/addresses \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"full_address":"123 Main","province_id":79,"district_id":760,"phone":"0901234568","is_default":true}')
ADDRESS_ID=$(echo $ADDRESS | jq '.id')

# Checkout
echo "Checking out..."
ORDER=$(curl -s -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"address_id":'$ADDRESS_ID',"item_ids":["item_1"],"use_loyalty_points":false}')

ORDER_ID=$(echo $ORDER | jq '.orders[0].order_id')
echo "Order: $ORDER_ID"

# Simulate payment
echo "Paying..."
stripe trigger payment_intent.succeeded
sleep 5

# Check result
RESULT=$(curl -s -X GET http://localhost:8080/api/v1/orders/$ORDER_ID \
  -H "Authorization: Bearer $BUYER_TOKEN" | jq -r '.status')
echo "Status: $RESULT (should be PAID) ✅"
```

---

## Verify Money Received (Stripe)

### Check in Stripe Dashboard

```bash
# 1. https://dashboard.stripe.com/test/payments
# 2. See PaymentIntents (status: succeeded)
# 3. Click to see Charges
# 4. Check Amount & Status
```

### Check via API

```bash
STRIPE_KEY="sk_test_xxxxx"

# Recent payments
curl -s https://api.stripe.com/v1/payment_intents \
  -u $STRIPE_KEY: -d limit=3 | jq '.data[] | {id, amount, status}'

# Charges
curl -s https://api.stripe.com/v1/charges \
  -u $STRIPE_KEY: -d limit=3 | jq '.data[] | {id, amount, paid}'

# Transfers to sellers
curl -s https://api.stripe.com/v1/transfers \
  -u $STRIPE_KEY: -d limit=3 | jq '.data[] | {id, amount, destination}'

# Account balance
curl -s https://api.stripe.com/v1/balance \
  -u $STRIPE_KEY: | jq '{available: .available[0], pending: .pending[0]}'
```

### Check in Database

```bash
# Transactions
psql -h localhost -U postgres -d flashsale_platform \
  -c "SELECT id, parent_order_id, status, stripe_pi_id, amount FROM transactions ORDER BY id DESC LIMIT 5;"

# Seller transfers
psql -h localhost -U postgres -d flashsale_platform \
  -c "SELECT id, seller_id, amount, stripe_transfer_id, status FROM seller_transfers ORDER BY id DESC LIMIT 5;"
```

### Check Kafka Events

```bash
# Payment success
docker exec stealing-from-paradise-kafka-1 \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic payment.success \
    --from-beginning \
    --max-messages 3

# Seller transfer created
docker exec stealing-from-paradise-kafka-1 \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic seller.transfer.created \
    --from-beginning \
    --max-messages 3
```

---

## Test Cards

| Scenario | Card | Exp | CVC | Result |
|----------|------|-----|-----|--------|
| Success | 4242 4242 4242 4242 | 12/26 | 123 | ✅ OK |
| Decline | 4000 0000 0000 0002 | 12/26 | 123 | ❌ Fail |
| 3D Secure | 4000 0025 0000 3155 | 12/26 | 123 | ⚠️ Auth |

---

## Flow Diagram

```
BUYER CHECKOUT
    ↓
API Gateway
    ↓
Order Service (creates order)
    ↓
Kafka: order.created
    ↓
Payment Service (listens)
    ↓
Payment Service → Stripe API (create PaymentIntent)
    ↓
Kafka: payment.requested
    ↓
Buyer receives PaymentIntent ID
    ↓
Frontend: Show Stripe Payment Form
    ↓
Buyer enters card: 4242 4242 4242 4242
    ↓
Frontend → Stripe (confirm payment)
    ↓
Stripe webhook: payment_intent.succeeded
    ↓
Payment Service (listens webhook)
    ↓
Payment Service → Stripe (create Transfer to seller)
    ↓
Kafka: payment.success
    ↓
Order Service (listens)
    ↓
Update Order Status: PENDING → PAID
    ↓
Kafka: order.paid
    ↓
Seller receives order notification ✅
```

---

## Troubleshooting

### "Webhook not received"

```bash
# Check Stripe CLI running
# Terminal should show: > Ready! Your webhook signing secret is...

# Restart
stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks
```

### "PaymentIntent not created"

```bash
# Check config
grep STRIPE .env

# Check logs
docker-compose logs payment-service | grep -i stripe

# Test connection
curl https://api.stripe.com/v1/payment_intents \
  -u sk_test_xxxxx: -d amount=1000 -d currency=usd
```

### "Order status still PENDING"

```bash
# Check payment service logs
docker-compose logs payment-service

# Check Axon saga
# Browser: http://localhost:8124/

# Check Kafka
docker exec stealing-from-paradise-kafka-1 \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic payment.success \
    --from-beginning
```

---

## Complete Flow Summary

```
PAYMENT FLOW:
1. Setup (5 min) → Stripe account + CLI + .env + Docker restart
2. Test Buyer (5 min) → Register, add product, checkout, pay
3. Verify (2 min) → Check Stripe dashboard + DB + Kafka

TOTAL: ~12 minutes ⏱️

EXPECTED RESULTS:
✅ Order status: PENDING → PAID
✅ Stripe PaymentIntent: succeeded
✅ Seller receives transfer
✅ Kafka events: payment.success + seller.transfer.created
✅ No errors in logs
```

---

## Files & Links

- **Full Guide**: `docs/TESTING_GUIDE_VN.md`
- **Detailed Saga**: `docs/PAYMENT_SAGA_STRIPE_SANDBOX_TESTING.md`
- **Stripe Dashboard**: https://dashboard.stripe.com/
- **Stripe CLI Docs**: https://stripe.com/docs/stripe-cli
- **Stripe Test Cards**: https://stripe.com/docs/testing

---

**Ready? Let's go! 🚀**

```bash
# 1. Setup
docker-compose up -d

# 2. Test
./test_payment.sh

# 3. Verify
curl http://localhost:8080/api/v1/payments/...
```

