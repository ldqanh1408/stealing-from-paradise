# Payment Saga Testing Guide: Stripe Sandbox

**Date**: 2026-04-18  
**Purpose**: Complete testing guide for payment saga flow using Stripe sandbox

---

## Prerequisites

### Software Requirements
- Docker & Docker Compose
- Java 21+
- Maven 3.8+
- curl or Postman
- Git

### Stripe Account
- Create free account at https://dashboard.stripe.com/register
- Enable "Viewing test data" in top-left corner
- Navigate to Developers → API Keys
- Copy **Publishable Key** and **Secret Key** (test mode)

---

## Part 1: Local Environment Setup

### 1.1 Clone & Navigate to Project

```bash
git clone https://github.com/your-org/stealing-from-paradise.git
cd stealing-from-paradise
```

### 1.2 Create `.env` File from Template

```bash
cp .env.example .env
```

### 1.3 Configure Stripe Keys in `.env`

Edit `.env` and set Stripe credentials:

```env
# Stripe Configuration
STRIPE_SECRET_KEY=sk_test_xxxxx...  # From Stripe Dashboard
STRIPE_WEBHOOK_SECRET=whsec_xxxxx... # Create after webhook endpoint setup
STRIPE_PLATFORM_FEE_PERCENTAGE=5.0

# Other required vars
EUREKA_URI=http://localhost:8761/eureka/
KAFKA_SERVER=localhost:9092
AXON_SERVER=localhost:8124
DB_HOST=localhost
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password
REDIS_HOST=localhost
JWT_SECRET=your-jwt-secret-key-here-min-32-chars
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=86400000
```

### 1.4 Start Infrastructure via Docker Compose

```bash
docker-compose up -d
```

**Services started**:
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`
- Axon Server: `localhost:8124`
- Zookeeper: `localhost:2181`

**Verify services**:

```bash
# Check running containers
docker-compose ps

# View logs
docker-compose logs -f kafka
docker-compose logs -f postgres
```

### 1.5 Build & Start Backend Services

```bash
# Build all services
cd backend
mvn clean install -DskipTests

# Terminal 1: Start discovery-service (Eureka)
cd discovery-service
mvn spring-boot:run

# Terminal 2: Start api-gateway
cd api-gateway
mvn spring-boot:run

# Terminal 3: Start identity-service
cd identity-service
mvn spring-boot:run

# Terminal 4: Start payment-service
cd payment-service
mvn spring-boot:run

# Terminal 5: Start order-service
cd order-service
mvn spring-boot:run

# Terminal 6: Start other services (optional for full system)
cd product-service && mvn spring-boot:run
cd cart-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
```

**Verify services are running**:

```bash
# Check Eureka Dashboard
curl http://localhost:8761/
# Should show registered services

# Check API Gateway
curl http://localhost:8080/actuator/health
# Response: {"status":"UP"}
```

### 1.6 Frontend Setup (Optional)

```bash
# Terminal 7: Start customer app
cd frontend/apps/customer
npm install
npm run dev

# Terminal 8: Start seller app
cd frontend/apps/seller
npm install
npm run dev
```

---

## Part 2: Stripe Sandbox Test Cards

### Test Payment Scenarios

| Scenario | Card Number | Exp | CVC | Result |
|----------|-------------|-----|-----|--------|
| **Success** | `4242 4242 4242 4242` | `12/26` | `123` | ✅ Payment succeeds |
| **Decline** | `4000 0000 0000 0002` | `12/26` | `123` | ❌ Card declined |
| **3D Secure** | `4000 0025 0000 3155` | `12/26` | `123` | ⚠️ Requires authentication |
| **Insufficient Funds** | `4000 0000 0000 9995` | `12/26` | `123` | ❌ Insufficient funds |

**Using in Stripe Elements**:
- Card number: Copy from table
- Expiry: Any future date
- CVC: Any 3 digits
- Name: Any value (e.g., "Test User")

---

## Part 3: Setup Stripe Webhook (Local Testing)

### 3.1 Install Stripe CLI

**macOS**:
```bash
brew install stripe/stripe-cli/stripe
```

**Linux**:
```bash
wget https://github.com/stripe/stripe-cli/releases/download/v1.x.x/stripe_1.x.x_linux_x86_64.tar.gz
tar -xvzf stripe_1.x.x_linux_x86_64.tar.gz
sudo mv stripe /usr/local/bin/
```

**Windows** (PowerShell):
```powershell
# Using chocolatey
choco install stripe-cli

# Or download from: https://github.com/stripe/stripe-cli/releases
```

### 3.2 Login to Stripe CLI

```bash
stripe login
# Follow the browser prompt to authorize
```

### 3.3 Forward Stripe Webhooks to Local

**Terminal 9: In project root**:

```bash
stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks
```

**Output**:
```
> Ready! Your webhook signing secret is: whsec_test_xxxxx...
```

### 3.4 Copy Webhook Secret to `.env`

```env
STRIPE_WEBHOOK_SECRET=whsec_test_xxxxx...
```

**Restart payment-service** to pick up new secret:

```bash
# In payment-service terminal, Ctrl+C and restart
cd backend/payment-service
mvn spring-boot:run
```

---

## Part 4: Prepare Test Data

### 4.1 Create Test User (Buyer)

```bash
# Register buyer
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "buyer_test",
    "email": "buyer@test.local",
    "password": "Password123!",
    "phone": "0901234567"
  }'

# Response:
# {
#   "user_id": 42,
#   "access_token": "eyJhbGc...",
#   "refresh_token": "..."
# }

# Save access_token for later use
export BUYER_TOKEN="eyJhbGc..."
export BUYER_ID="42"
```

### 4.2 Create Test Seller & Add Products

```bash
# Register seller
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "seller_test",
    "email": "seller@test.local",
    "password": "Password123!",
    "phone": "0901234568",
    "role": "SELLER"
  }'

# Save seller token
export SELLER_TOKEN="eyJhbGc..."
export SELLER_ID="43"

# Add product to seller inventory
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sku_code": "TEST-PROD-001",
    "name": "Test Product",
    "description": "Test product for saga testing",
    "price": 1200000,
    "stock": 10,
    "category_id": 1
  }'

# Response: { "product_id": 100, ... }
export PRODUCT_ID="100"
```

### 4.3 Create Buyer Address

```bash
curl -X POST http://localhost:8080/api/v1/addresses \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "full_address": "123 Test Street, Ho Chi Minh City",
    "province_id": 79,
    "district_id": 760,
    "phone": "0901234567",
    "is_default": true
  }'

# Response: { "address_id": 1 }
export ADDRESS_ID="1"
```

### 4.4 Add Product to Cart

```bash
curl -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "product_id": 100,
    "variant_id": 1,
    "quantity": 2
  }'

# Response: { "cart_item_id": "item_1" }
export CART_ITEM_ID="item_1"
```

---

## Part 5: Execute Payment Saga Test

### 5.1 Call Checkout API

```bash
# Step 1: Initiate checkout
curl -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "address_id": 1,
    "item_ids": ["item_1"],
    "use_loyalty_points": false,
    "loyalty_points_to_use": 0
  }'
```

**Response**:
```json
{
  "parent_order_id": 55,
  "order_code": "PO-20260418-55",
  "orders": [
    {
      "order_id": 100,
      "order_code": "OR-20260418-100",
      "seller_id": 43,
      "seller_name": "Test Seller",
      "total_amt": 2400000,
      "status": "PENDING"
    }
  ],
  "payment": {
    "total_amount": 2400000,
    "final_amount": 2400000,
    "currency": "VND"
  },
  "payment_status": "PENDING",
  "timeout_at": "2026-04-18T12:00:00Z"
}
```

Save for reference:
```bash
export PARENT_ORDER_ID="55"
export ORDER_ID="100"
```

### 5.2 Verify Saga Started (Axon Server)

Open browser: http://localhost:8124/

**Expected**:
- See `ParentOrderPaymentSaga` with instance ID (parentOrderId)
- Status: **Running**
- Events recorded: `ParentOrderCheckoutCreatedEvent`

### 5.3 Verify payment.requested Published

**Check Kafka topic**:

```bash
# View messages in payment.requested topic
docker exec stealing-from-paradise-kafka-1 \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic payment.requested \
    --from-beginning

# Should see:
# {
#   "parent_order_id": 55,
#   "user_id": 42,
#   "total_amount": 2400000,
#   "currency": "VND",
#   "timeout_at": "2026-04-18T12:00:00",
#   "timestamp": "..."
# }
```

### 5.4 Check Payment Service Logs

**Look for**:
```
INFO [PaymentService] Payment initialized: parentOrderId=55, txId=123, piId=pi_test_xxxxx
```

This confirms:
- ✅ Stripe PaymentIntent created
- ✅ Transaction saved with status PENDING

### 5.5 Get Transaction & PaymentIntent Details

```bash
# Query transaction
curl -X GET http://localhost:8080/api/v1/payments/parent-order/55 \
  -H "Authorization: Bearer $BUYER_TOKEN"

# Response:
# {
#   "transaction_id": 123,
#   "parent_order_id": 55,
#   "amount": 2400000,
#   "status": "PENDING",
#   "stripe_pi_id": "pi_test_xxxxx",
#   "remaining_seconds": 599,
#   "sellers": [...]
# }
```

**Note**: `remaining_seconds` counts down from 600 (10 minutes)

### 5.6 Simulate Customer Payment

In real scenario, customer completes payment in Stripe Elements on frontend.  
For testing, use Stripe Dashboard:

**Option A: Using Stripe Dashboard**

1. Go to https://dashboard.stripe.com/test/payments
2. Find the PaymentIntent (search by pi_test_xxxxx)
3. Click "Confirm payment"
4. Use test card: `4242 4242 4242 4242`
5. Click "Pay"

**Option B: Simulate via Stripe CLI** (Recommended)

```bash
# Trigger payment_intent.succeeded webhook
stripe trigger payment_intent.succeeded \
  --add payment_intent:metadata='{"parent_order_id": "55", "user_id": "42"}'
```

### 5.7 Verify Payment Success Event Flow

**Check Stripe CLI output** (from terminal where stripe listen runs):
```
> payment_intent.succeeded [evt_test_xxxxx]
```

**Check payment-service logs**:
```
INFO [PaymentService] Payment succeeded: parentOrderId=55, piId=pi_test_xxxxx
INFO [PaymentService] Publishing payment.success event
```

**Check Kafka**:

```bash
docker exec stealing-from-paradise-kafka-1 \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic payment.success \
    --from-beginning

# Should see:
# {
#   "parent_order_id": 55,
#   "transaction_id": 123,
#   "stripe_pi_id": "pi_test_xxxxx",
#   "amount": 2400000
# }
```

### 5.8 Verify Saga Completion

**Check Axon Server** (http://localhost:8124/):
- `ParentOrderPaymentSaga` status: **Completed** (no longer Running)
- Events: `ParentOrderPaymentSucceededEvent` recorded

**Check order-service logs**:
```
INFO [ParentOrderPaymentSaga] Payment succeeded, updated 1 sub-orders
INFO [OrderProcessingSaga][100] PAID
```

### 5.9 Query Order Status

```bash
# Get order details
curl -X GET http://localhost:8080/api/v1/orders/100 \
  -H "Authorization: Bearer $BUYER_TOKEN"

# Response:
# {
#   "order_id": 100,
#   "status": "PAID",  ← Changed from PENDING!
#   "order_code": "OR-20260418-100",
#   "seller_id": 43,
#   "total_amt": 2400000,
#   "...": "..."
# }
```

✅ **Payment saga completed successfully!**

---

## Part 6: Test Failure Scenarios

### 6.1 Test Payment Failure

**Setup**:

```bash
# Repeat checkout steps with failure test card
export BUYER_TOKEN="..." # Use previous buyer token

# Add product to cart again
curl -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "product_id": 100,
    "variant_id": 1,
    "quantity": 1
  }'

# Checkout
curl -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "address_id": 1,
    "item_ids": ["item_2"],
    "use_loyalty_points": false,
    "loyalty_points_to_use": 0
  }'

# Save new parent_order_id
export PARENT_ORDER_ID_FAIL="56"
```

**Trigger payment failure**:

```bash
# Use Stripe Dashboard with decline card: 4000 0000 0000 0002
# OR via CLI:
stripe trigger payment_intent.payment_failed \
  --add payment_intent:metadata='{"parent_order_id": "56", "user_id": "42"}'
```

**Verify failure handling**:

```bash
# Check order status
curl -X GET http://localhost:8080/api/v1/orders/101 \
  -H "Authorization: Bearer $BUYER_TOKEN"

# Expected status: CANCELLED
# Expected cancelledBy: SYSTEM
# Expected cancelReason: Thanh toan that bai
```

**Check logs**:
```
INFO [ParentOrderPaymentSaga] Payment failed, cancelled 1 sub-orders
INFO [OrderService] onPaymentFailed: parentOrderId=56
```

### 6.2 Test Payment Timeout

**Setup**:

```bash
# Initiate checkout with success card but don't complete payment
curl -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "address_id": 1,
    "item_ids": ["item_3"],
    "use_loyalty_points": false,
    "loyalty_points_to_use": 0
  }'

export PARENT_ORDER_ID_TIMEOUT="57"
export ORDER_ID_TIMEOUT="102"
```

**Wait for timeout** (30 minutes in production, configurable in code):

For testing, modify `OrderService.PAYMENT_TIMEOUT_MINUTES`:

```java
// backend/order-service/src/main/java/com/flashsale/orderdomain/service/OrderService.java
private static final int PAYMENT_TIMEOUT_MINUTES = 1; // Change to 1 for testing
```

**Rebuild & restart**:

```bash
cd backend/order-service
mvn clean package -DskipTests
mvn spring-boot:run
```

**Wait 1 minute** (or however many seconds you configured):

**Check order auto-cancelled**:

```bash
curl -X GET http://localhost:8080/api/v1/orders/102 \
  -H "Authorization: Bearer $BUYER_TOKEN"

# Expected status: CANCELLED
# Expected cancelReason: Payment timeout
# Expected cancelledBy: SYSTEM
```

**Check Kafka**:

```bash
docker exec stealing-from-paradise-kafka-1 \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic order.auto_cancelled \
    --from-beginning
```

---

## Part 7: Idempotency Testing

### 7.1 Test Double Webhook

**Goal**: Verify payment.requested is not processed twice

**Setup**:

```bash
# Checkout
curl -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{...}'

export PARENT_ORDER_ID_IDEMPOTENT="58"
```

**Send payment.requested twice**:

```bash
# First time (via Kafka naturally)
# Check logs: Payment initialized: txId=124, piId=pi_test_yyyyy

# Manually send duplicate
docker exec stealing-from-paradise-kafka-1 \
  kafka-console-producer.sh \
    --broker-list localhost:9092 \
    --topic payment.requested \
    < payment_requested_duplicate.json

# Check logs: Skip payment.requested because transaction already exists
```

**Verification**: Only one Stripe PaymentIntent created, no duplicate.

---

## Part 8: Debugging & Monitoring

### 8.1 View Saga State (Axon Server UI)

```
URL: http://localhost:8124/
```

**Features**:
- View all running sagas
- See event history per saga
- Inspect payload of each event
- Deadletter queue for failed events

### 8.2 Check Service Logs

```bash
# order-service
tail -f backend/order-service/target/logs/app.log

# payment-service
tail -f backend/payment-service/target/logs/app.log

# Check for keywords:
# "ParentOrderPaymentSaga"
# "Payment initialized"
# "Payment succeeded"
# "onPaymentFailed"
```

### 8.3 Database Queries

```bash
# Connect to PostgreSQL (order-service schema)
psql -h localhost -U postgres -d flashsale_platform -c \
  "SELECT id, user_id, parent_order_id, status FROM orders WHERE parent_order_id = 55;"

# Connect to PostgreSQL (payment-service schema)
psql -h localhost -U postgres -d flashsale_platform -c \
  "SELECT id, parent_order_id, status, stripe_pi_id FROM transactions WHERE parent_order_id = 55;"
```

### 8.4 Kafka Topic Monitoring

```bash
# List all topics
docker exec stealing-from-paradise-kafka-1 \
  kafka-topics.sh --list --bootstrap-server localhost:9092

# Describe topic
docker exec stealing-from-paradise-kafka-1 \
  kafka-topics.sh --describe --topic payment.requested --bootstrap-server localhost:9092

# Count messages in topic
docker exec stealing-from-paradise-kafka-1 \
  kafka-run-class.sh kafka.tools.JmxTool \
    --object-name kafka.server:type=ReplicaManager,name=UnderReplicatedPartitions
```

---

## Part 9: Common Issues & Solutions

### Issue 1: "PaymentIntent not created"

**Symptom**:
- Stripe PaymentIntent ID is null in transaction
- Logs: `Failed to initialize payment from payment.requested`

**Solutions**:
1. Check Stripe API key in `.env` is valid
2. Verify Stripe SDK version in pom.xml (>= 26.1.0)
3. Check payment-service logs for Stripe exception
4. Verify network connectivity to api.stripe.com

```bash
# Test Stripe connection
curl https://api.stripe.com/v1/payment_intents \
  -u sk_test_xxxxx: \
  -d amount=1000 \
  -d currency=usd
```

### Issue 2: "Webhook not received"

**Symptom**:
- Payment completes in Stripe Dashboard
- `payment.success` topic is empty
- Order status remains PENDING

**Solutions**:
1. Verify Stripe CLI is running: `stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks`
2. Check webhook secret matches in `.env`
3. Verify payment-service is reachable: `curl http://localhost:8082/actuator/health`
4. Check payment-service logs for webhook handler errors

### Issue 3: "Saga not starting"

**Symptom**:
- Axon Server shows no ParentOrderPaymentSaga instance
- No `payment.requested` in Kafka

**Solutions**:
1. Verify OrderService emits ParentOrderCheckoutCreatedEvent
2. Check order-service logs for Axon initialization
3. Verify EventGateway is autowired correctly
4. Restart order-service and Axon Server

```bash
# Restart Axon
docker-compose restart axonserver

# Restart order-service
cd backend/order-service && mvn spring-boot:run
```

### Issue 4: "Transaction already exists" after retry

**Symptom**:
- Payment.requested sent twice (Kafka retry)
- Second time: "Skip payment.requested because transaction already exists"

**Expected**: This is correct behavior (idempotency).

**Verification**:
- Check that only one Stripe PaymentIntent was created
- Confirm saga still completes successfully
- Verify order status is PAID after payment succeeds

---

## Part 10: Performance & Load Testing

### 10.1 Single Order Flow Benchmark

```bash
# Measure time for checkout → payment → order.paid

time curl -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{...}'

# Expected: < 2 seconds
```

### 10.2 Concurrent Checkouts

```bash
#!/bin/bash
# test_concurrent.sh

for i in {1..10}; do
  curl -X POST http://localhost:8080/api/v1/orders/checkout \
    -H "Authorization: Bearer $BUYER_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{...}" &
done
wait

# Monitor: top, docker stats, Axon Server UI
```

### 10.3 Saga Completion Time

**Measure** (from event publish to saga end):

```bash
# Grep logs for timestamps
grep "ParentOrderPaymentSaga.*Payment requested" order-service.log
grep "ParentOrderPaymentSaga.*Payment succeeded" order-service.log

# Calculate delta: completion_time - request_time
```

Expected: < 1 second (assuming fast network to Stripe)

---

## Part 11: Cleanup & Reset

### 11.1 Stop Services

```bash
# Stop all services
Ctrl+C in each terminal

# Or stop Docker containers
docker-compose down

# Remove volumes (reset DB)
docker-compose down -v
```

### 11.2 Clear Test Data

```bash
# Delete all orders
psql -h localhost -U postgres -d flashsale_platform << EOF
  DELETE FROM order_items;
  DELETE FROM orders;
  DELETE FROM parent_orders;
  DELETE FROM transactions;
  DELETE FROM seller_transfers;
  DELETE FROM refunds;
EOF
```

### 11.3 Restart Clean

```bash
# Fresh start
docker-compose up -d
cd backend && mvn clean install -DskipTests

# Restart all services
# (Follow Part 1.5 again)
```

---

## Checklist: Full End-to-End Test

- [ ] **Setup**
  - [ ] Docker containers running
  - [ ] All backend services registered in Eureka
  - [ ] Stripe CLI forwarding webhooks
  - [ ] Test data created (buyer, seller, products)

- [ ] **Happy Path (Success)**
  - [ ] Checkout creates ParentOrder + SubOrders
  - [ ] ParentOrderPaymentSaga starts
  - [ ] payment.requested published to Kafka
  - [ ] PaymentService creates Stripe PaymentIntent
  - [ ] Customer completes payment (test card 4242...)
  - [ ] Stripe webhook received (payment_intent.succeeded)
  - [ ] payment.success published to Kafka
  - [ ] ParentOrderPaymentSaga completes
  - [ ] All sub-orders status = PAID
  - [ ] OrderProcessingSaga cancels payment timeout

- [ ] **Failure Path (Decline)**
  - [ ] Checkout with decline card (4000 0000 0000 0002)
  - [ ] PaymentService initialized payment
  - [ ] Stripe webhook received (payment_intent.payment_failed)
  - [ ] payment.failed published
  - [ ] All sub-orders status = CANCELLED
  - [ ] ParentOrderPaymentSaga completed

- [ ] **Timeout Path**
  - [ ] Checkout without completing payment
  - [ ] Wait for payment timeout deadline (>1 min with config change)
  - [ ] OrderProcessingSaga.onPaymentTimeout fires
  - [ ] All sub-orders status = CANCELLED
  - [ ] order.auto_cancelled published

- [ ] **Idempotency**
  - [ ] Duplicate payment.requested handled correctly
  - [ ] Only one Stripe PaymentIntent created
  - [ ] No duplicate transactions in DB

- [ ] **Monitoring**
  - [ ] Axon Server shows saga lifecycle
  - [ ] Kafka topics have expected messages
  - [ ] Logs show proper event sequence
  - [ ] No errors in services

---

## Next Steps After Testing

1. **Code Coverage**: Run tests to verify saga logic
   ```bash
   cd backend/order-service
   mvn test -Dtest=*PaymentSaga*
   ```

2. **Integration Tests**: Add multi-service tests
   ```bash
   cd backend
   mvn verify  # Runs integration tests
   ```

3. **Performance Profile**: Identify bottlenecks
   - Check Stripe API latency
   - Monitor Kafka message throughput
   - Measure saga event processing time

4. **Production Readiness**:
   - Switch to live Stripe keys (not test keys)
   - Enable distributed tracing (Jaeger/Zipkin)
   - Configure alerts on failed sagas
   - Set up payment reconciliation job

---

## References

- **Stripe Documentation**: https://stripe.com/docs/payments/payment-intents
- **Stripe Test Cards**: https://stripe.com/docs/testing
- **Stripe CLI**: https://stripe.com/docs/stripe-cli
- **Axon Framework Sagas**: https://docs.axoniq.io/reference-guide/axon-framework/advanced-concepts/saga
- **Project Payment Saga Docs**: `docs/06_PAYMENT_SAGA_FLOW.md`

