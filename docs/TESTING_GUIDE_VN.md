# 🧪 HƯỚNG DẪN TEST - Flash Sale E-Commerce Platform

**Ngày**: 2026-04-19  
**Phiên Bản**: 1.0  
**Mục Đích**: Test toàn bộ hệ thống một cách đơn giản & dễ hiểu

---

## 📖 MỤC LỤC

1. [Chuẩn Bị - 5 Phút](#chuẩn-bị)
2. [Setup Payment - Chi Tiết](#setup-payment)
3. [Test Backend - Unit & Integration](#test-backend)
4. [Test Frontend - Thủ Công](#test-frontend)
5. [Test Luồng Đơn Hàng (Happy Path)](#test-happy-path)
6. [Test Thanh Toán Stripe - Chi Tiết](#test-stripe)
7. [Test Thanh Toán từ Buyer - End-to-End](#test-buyer-payment)
8. [Nhận Tiền từ Stripe](#receive-payment-stripe)
9. [Khắc Phục Lỗi](#troubleshooting)

---

## Chuẩn Bị

### Bước 1: Khởi Động Docker

```bash
# Đi đến thư mục project
cd stealing-from-paradise

# Khởi động tất cả services (infrastructure + backend + frontend)
docker-compose up -d

# Chờ 3-5 phút cho mọi service khởi động
docker-compose ps  # Kiểm tra trạng thái
```

**Kiểm tra xem services có chạy không:**
```bash
# API Gateway
curl http://localhost:8080/actuator/health
# Kết quả: {"status":"UP"}

# Eureka Dashboard
curl http://localhost:8761/
# Trình duyệt: http://localhost:8761/
```

### Bước 2: Setup Local Backend (Nếu Cần)

```bash
# Nếu muốn chạy services trên máy local thay vì Docker
cd backend

# Build tất cả services
mvn clean install -DskipTests

# Chạy Eureka Discovery Service (Terminal 1)
cd discovery-service
mvn spring-boot:run

# Chạy API Gateway (Terminal 2)
cd ../api-gateway
mvn spring-boot:run

# Chạy Identity Service (Terminal 3)
cd ../identity-service
mvn spring-boot:run

# Chạy Order Service (Terminal 4)
cd ../order-service
mvn spring-boot:run

# Chạy Payment Service (Terminal 5)
cd ../payment-service
mvn spring-boot:run
```

---

## Setup Payment - Chi Tiết

### Bước 1: Tạo Stripe Account

```bash
# 1. Truy cập https://dashboard.stripe.com/register
# 2. Tạo tài khoản FREE
# 3. Verify email
# 4. Login vào Dashboard

# Trong Dashboard:
# - Click "Developers" (góc trên phải)
# - Click "API Keys"
# - Mode: Test Mode (chuyển sang test)
# - Lưu 2 keys này:
#   - Publishable Key: pk_test_xxxxx
#   - Secret Key: sk_test_xxxxx
```

### Bước 2: Setup Stripe CLI (Webhook Forwarding)

```bash
# ===== CÀI ĐẶT STRIPE CLI =====

# macOS
brew install stripe/stripe-cli/stripe

# Windows (PowerShell)
choco install stripe-cli

# Linux
# Tải từ: https://github.com/stripe/stripe-cli/releases

# ===== LOGIN STRIPE CLI =====
stripe login
# Theo dõi prompt trong trình duyệt -> Authorize

# ===== FORWARD WEBHOOKS =====
# Terminal riêng biệt (giữ luôn chạy!)
stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks

# Kết quả:
# > Ready! Your webhook signing secret is: whsec_test_xxxxx...
# ⚠️ LƯU webhook secret này!
```

### Bước 3: Cấu Hình .env

```bash
# Mở file .env
nano .env  # hoặc dùng editor

# Thêm/Sửa các dòng:
STRIPE_SECRET_KEY=sk_test_xxxxx                    # Từ Stripe Dashboard
STRIPE_PUBLISHABLE_KEY=pk_test_xxxxx               # Từ Stripe Dashboard
STRIPE_WEBHOOK_SECRET=whsec_test_xxxxx             # Từ stripe listen output
STRIPE_PLATFORM_FEE_PERCENTAGE=5.0                 # Platform fee 5%

# Ví dụ hoàn chỉnh:
STRIPE_SECRET_KEY=sk_test_51234567890abcdefghijk
STRIPE_PUBLISHABLE_KEY=pk_test_51234567890abcdefghijk
STRIPE_WEBHOOK_SECRET=whsec_test_1234567890abcdefghijk
STRIPE_PLATFORM_FEE_PERCENTAGE=5.0

# Database & Infrastructure
EUREKA_URI=http://localhost:8761/eureka/
KAFKA_SERVER=localhost:9092
AXON_SERVER=localhost:8124
DB_HOST=localhost
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password
REDIS_HOST=localhost
JWT_SECRET=your-jwt-secret-key-here-min-32-chars-long
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=86400000
```

### Bước 4: Restart Services với Stripe Config

```bash
# Nếu chạy Docker:
docker-compose down
docker-compose up -d
# Chờ 3-5 phút

# Nếu chạy local:
# Restart payment-service (trong Terminal nơi chạy payment-service)
Ctrl+C
cd backend/payment-service
mvn spring-boot:run
```

### Bước 5: Xác Nhận Setup

```bash
# Kiểm tra Payment Service chạy
curl http://localhost:8082/actuator/health
# Kết quả: {"status":"UP"}

# Kiểm tra Stripe Config
# Xem logs payment-service
docker-compose logs payment-service | grep -i stripe

# Hoặc nếu chạy local, xem logs trong terminal payment-service
# Tìm dòng: "Stripe configured with secret key: sk_test_..."
```

---

## Test Backend

### Unit Tests - Test Từng Service

```bash
# Test Identity Service
cd backend/identity-service
mvn test

# Test Order Service
cd backend/order-service
mvn test

# Test một class cụ thể
mvn test -Dtest=UserServiceTest

# Test một method cụ thể
mvn test -Dtest=UserServiceTest#testCreateUserSuccess
```

### Integration Tests - Test Nhiều Services Cùng Nhau

```bash
# Test tất cả services
cd backend
mvn verify

# Test một service cụ thể
cd backend/order-service
mvn verify

# Test Order Service integration
mvn verify -Dit.test=OrderServiceIT
```

### Code Coverage - Kiểm Tra Coverage

```bash
cd backend/order-service

# Chạy tests với coverage report
mvn clean test jacoco:report

# Xem report (mở file này trong trình duyệt)
# target/site/jacoco/index.html
```

### Axon Framework Tests (Payment Saga)

```bash
cd backend/order-service

# Test Payment Saga
mvn test -Dtest=ParentOrderPaymentSagaTest

# Test Command Handlers
mvn test -Dtest=CreateOrderCommandHandlerTest

# Test Event Handlers
mvn test -Dtest=OrderCreatedEventHandlerTest
```

---

## Test Frontend

### Setup Frontend

```bash
# Terminal 1: Customer App
cd frontend/apps/customer
npm install
npm run dev
# Truy cập: http://localhost:3000

# Terminal 2: Seller App
cd frontend/apps/seller
npm install
npm run dev
# Truy cập: http://localhost:3001

# Terminal 3: Admin App
cd frontend/apps/admin
npm install
npm run dev
# Truy cập: http://localhost:3002
```

### TypeScript Checking

```bash
# Kiểm tra TypeScript errors
cd frontend/apps/customer
npm run lint

# Check tất cả apps
for app in customer seller admin; do
  cd ../apps/$app
  npm run lint
done
```

### Build Frontend

```bash
cd frontend/apps/customer

# Production build
npm run build

# Check build size
ls -lh dist/
```

### Manual Testing Checklist - Customer App

**Đăng Ký & Đăng Nhập:**
- [ ] Đăng ký tài khoản mới
- [ ] Đăng nhập bằng email & password
- [ ] Logout thành công

**Duyệt & Tìm Kiếm Sản Phẩm:**
- [ ] Xem danh sách sản phẩm
- [ ] Tìm kiếm sản phẩm
- [ ] Lọc theo danh mục
- [ ] Xem chi tiết sản phẩm
- [ ] Xem hình ảnh sản phẩm

**Giỏ Hàng:**
- [ ] Thêm sản phẩm vào giỏ
- [ ] Cập nhật số lượng
- [ ] Xóa sản phẩm khỏi giỏ
- [ ] Xem tổng giá tiền

**Checkout & Thanh Toán:**
- [ ] Nhập địa chỉ giao hàng
- [ ] Chọn phương thức thanh toán
- [ ] Xem chi tiết đơn hàng
- [ ] Hoàn tất thanh toán

**Quản Lý Đơn Hàng:**
- [ ] Xem danh sách đơn hàng
- [ ] Xem chi tiết đơn hàng
- [ ] Theo dõi vận chuyển
- [ ] Hoàn trả/Refund

### Manual Testing Checklist - Seller App

**Quản Lý Sản Phẩm:**
- [ ] Thêm sản phẩm mới
- [ ] Chỉnh sửa sản phẩm
- [ ] Xóa sản phẩm
- [ ] Quản lý tồn kho (stock)
- [ ] Thêm biến thể sản phẩm (variant)

**Quản Lý Đơn Hàng:**
- [ ] Xem danh sách đơn hàng
- [ ] Xem chi tiết đơn hàng
- [ ] Cập nhật trạng thái đơn hàng
- [ ] In hóa đơn

**Flash Sale:**
- [ ] Xem Flash Sale sessions
- [ ] Tham gia Flash Sale
- [ ] Xem sales analytics

---

## Test Thanh Toán Stripe - Chi Tiết

### Chuẩn Bị Stripe CLI

```bash
# 1. Tải Stripe CLI
# macOS: brew install stripe/stripe-cli/stripe
# Windows: choco install stripe-cli
# Linux: Tải từ https://github.com/stripe/stripe-cli/releases

# 2. Login
stripe login
# Theo dõi prompt trong trình duyệt

# 3. Forward webhooks đến local (GIỮ LUN CHẠY!)
stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks

# Kết quả:
# > Ready! Your webhook signing secret is: whsec_test_xxxxx...
```

### Test Card Numbers

| Loại | Card Number | Exp | CVC | Kết Quả |
|------|-------------|-----|-----|---------|
| **Thành Công** | 4242 4242 4242 4242 | 12/26 | 123 | ✅ Payment OK |
| **Từ Chối** | 4000 0000 0000 0002 | 12/26 | 123 | ❌ Declined |
| **3D Secure** | 4000 0025 0000 3155 | 12/26 | 123 | ⚠️ Auth needed |

---

## Test Thanh Toán từ Buyer - End-to-End

### Buyer Payment Flow (Browser)

```bash
# ===== BƯỚC 1: BUYER CHECKOUT TỪ BROWSER =====

# 1. Mở http://localhost:3000 (Customer App)
# 2. Login hoặc Register
# 3. Browse sản phẩm
# 4. Click vào sản phẩm → "Add to Cart"
# 5. Xem giỏ hàng
# 6. Click "Checkout"
# 7. Nhập địa chỉ giao hàng → "Continue"
# 8. Review order

# ===== BƯỚC 2: PAYMENT PAGE =====

# 1. Payment page xuất hiện (Stripe Elements)
# 2. Điền card info:
#    - Card: 4242 4242 4242 4242
#    - Exp: 12/26
#    - CVC: 123
# 3. Click "Pay"
# 4. Chờ 3-5 giây
# 5. Should see "Payment Successful"
# 6. Order confirmation page
```

### Buyer Payment Flow (API)

```bash
#!/bin/bash
# test_buyer_payment.sh

echo "=== Buyer Payment Test ==="

# Setup data (seller & product)
SELLER=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"seller_'$(date +%s)'","email":"seller_'$(date +%s)'@test.local","password":"Password123!","phone":"0901234567","role":"SELLER"}')
SELLER_TOKEN=$(echo $SELLER | jq -r '.access_token')

PRODUCT=$(curl -s -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sku_code":"TEST-'$(date +%s)'","name":"Test Product","price":100000,"stock":10,"category_id":1}')
PRODUCT_ID=$(echo $PRODUCT | jq '.id')

# Buyer setup
BUYER=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"buyer_'$(date +%s)'","email":"buyer_'$(date +%s)'@test.local","password":"Password123!","phone":"0901234568"}')
BUYER_TOKEN=$(echo $BUYER | jq -r '.access_token')

# Add to cart
curl -s -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"product_id":'$PRODUCT_ID',"quantity":2}' > /dev/null

# Create address
ADDRESS=$(curl -s -X POST http://localhost:8080/api/v1/addresses \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"full_address":"123 Main St","province_id":79,"district_id":760,"phone":"0901234568","is_default":true}')
ADDRESS_ID=$(echo $ADDRESS | jq '.id')

# CHECKOUT
echo "Creating order..."
ORDER=$(curl -s -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"address_id":'$ADDRESS_ID',"item_ids":["item_1"],"use_loyalty_points":false}')

ORDER_ID=$(echo $ORDER | jq '.orders[0].order_id')
AMOUNT=$(echo $ORDER | jq '.payment.final_amount')
echo "✓ Order created: $ORDER_ID ($AMOUNT VND)"

# CHECK PAYMENT
echo ""
echo "Checking payment..."
PAYMENT=$(curl -s -X GET http://localhost:8080/api/v1/payments/parent-order/$(echo $ORDER | jq '.parent_order_id') \
  -H "Authorization: Bearer $BUYER_TOKEN")
PI=$(echo $PAYMENT | jq -r '.stripe_pi_id')
echo "✓ Stripe PaymentIntent: $PI"

# SIMULATE PAYMENT
echo ""
echo "Simulating payment (stripe CLI)..."
stripe trigger payment_intent.succeeded
sleep 5

# CHECK RESULT
echo ""
echo "Checking order status..."
FINAL_STATUS=$(curl -s -X GET http://localhost:8080/api/v1/orders/$ORDER_ID \
  -H "Authorization: Bearer $BUYER_TOKEN" | jq -r '.status')
echo "✓ Order Status: $FINAL_STATUS (should be PAID)"
```

---

## Nhận Tiền từ Stripe

### Bước 1: Kiểm Tra Transactions

```bash
# Database PostgreSQL
psql -h localhost -U postgres -d flashsale_platform \
  -c "SELECT id, parent_order_id, status, stripe_pi_id FROM transactions LIMIT 5;"

# Kết quả:
# id | parent_order_id | status | stripe_pi_id
# 1  | 55 | COMPLETED | pi_test_xxxxx
```

### Bước 2: Kiểm Tra Seller Transfers

```bash
# Xem transfers từ Stripe tới sellers
curl https://api.stripe.com/v1/transfers \
  -u sk_test_xxxxx: | jq '.data[] | {id, amount, destination, status}'

# Hoặc database:
psql -h localhost -U postgres -d flashsale_platform \
  -c "SELECT id, seller_id, amount, stripe_transfer_id, status FROM seller_transfers LIMIT 5;"
```

### Bước 3: Kiểm Tra Kafka Events

```bash
# Payment success event
docker exec stealing-from-paradise-kafka-1 \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic payment.success \
    --from-beginning \
    --max-messages 3

# Transfer created event
docker exec stealing-from-paradise-kafka-1 \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic seller.transfer.created \
    --from-beginning \
    --max-messages 3
```

### Bước 4: Verify Settlement (Full Script)

```bash
#!/bin/bash
# verify_payment_settlement.sh

STRIPE_KEY="sk_test_xxxxx"  # Thay bằng key của bạn

echo "=== Payment Settlement Verification ==="

# 1. PaymentIntents
echo ""
echo "1. Recent PaymentIntents:"
curl -s https://api.stripe.com/v1/payment_intents \
  -u $STRIPE_KEY: -d limit=3 | jq '.data[] | {id, amount, status}'

# 2. Charges
echo ""
echo "2. Recent Charges:"
curl -s https://api.stripe.com/v1/charges \
  -u $STRIPE_KEY: -d limit=3 | jq '.data[] | {id, amount, paid}'

# 3. Transfers
echo ""
echo "3. Recent Transfers (to sellers):"
curl -s https://api.stripe.com/v1/transfers \
  -u $STRIPE_KEY: -d limit=3 | jq '.data[] | {id, amount, destination}'

# 4. Balance
echo ""
echo "4. Current Balance:"
curl -s https://api.stripe.com/v1/balance \
  -u $STRIPE_KEY: | jq '{available, pending}'

# 5. Database check
echo ""
echo "5. Database Transactions:"
psql -h localhost -U postgres -d flashsale_platform -c \
  "SELECT COUNT(*), status FROM transactions GROUP BY status;"

echo ""
echo "=== Complete ==="
```

---

## Test Happy Path - Luồng Đơn Hàng Hoàn Chỉnh

### Scenario: Seller Thêm Sản Phẩm & Customer Mua

```bash
# ===== BƯỚC 1: Seller Thêm Sản Phẩm =====

# 1a. Seller đăng ký
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "seller_test_'$(date +%s)'",
    "email": "seller_'$(date +%s)'@test.local",
    "password": "Password123!",
    "phone": "0901234567",
    "role": "SELLER"
  }'

# Lưu: SELLER_TOKEN, SELLER_ID

# 1b. Seller thêm sản phẩm
export SELLER_TOKEN="<token_từ_trên>"
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sku_code": "PROD-'$(date +%s)'",
    "name": "Test Product",
    "description": "Product for testing",
    "price": 100000,
    "stock": 10,
    "category_id": 1
  }'

# Lưu: PRODUCT_ID

# ===== BƯỚC 2: Customer Mua Sản Phẩm =====

# 2a. Customer đăng ký
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "buyer_test_'$(date +%s)'",
    "email": "buyer_'$(date +%s)'@test.local",
    "password": "Password123!",
    "phone": "0901234568"
  }'

# Lưu: BUYER_TOKEN, BUYER_ID

# 2b. Customer thêm sản phẩm vào giỏ
export BUYER_TOKEN="<token_từ_trên>"
export PRODUCT_ID="<product_id_từ_bước_1b>"

curl -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "product_id": '$PRODUCT_ID',
    "quantity": 2
  }'

# 2c. Customer xem giỏ hàng
curl -X GET http://localhost:8080/api/v1/cart \
  -H "Authorization: Bearer $BUYER_TOKEN" | jq '.'

# 2d. Customer tạo địa chỉ giao hàng
curl -X POST http://localhost:8080/api/v1/addresses \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "full_address": "123 Main Street",
    "province_id": 79,
    "district_id": 760,
    "phone": "0901234568",
    "is_default": true
  }'

# Lưu: ADDRESS_ID

# 2e. Customer checkout
export ADDRESS_ID="<address_id_từ_trên>"

curl -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "address_id": '$ADDRESS_ID',
    "item_ids": ["item_1"],
    "use_loyalty_points": false,
    "loyalty_points_to_use": 0
  }' | jq '.'

# Lưu: ORDER_ID, PARENT_ORDER_ID

# ===== BƯỚC 3: Kiểm Tra Kết Quả =====

export ORDER_ID="<order_id_từ_trên>"

# 3a. Kiểm tra trạng thái đơn hàng
curl -X GET http://localhost:8080/api/v1/orders/$ORDER_ID \
  -H "Authorization: Bearer $BUYER_TOKEN" | jq '.status'

# Kết quả: "PAID" (nếu thanh toán thành công) hoặc "PENDING"

# 3b. Seller xem đơn hàng
curl -X GET http://localhost:8080/api/v1/orders/$ORDER_ID \
  -H "Authorization: Bearer $SELLER_TOKEN" | jq '.'

# ===== BƯỚC 4: Kiểm Tra Kafka Messages =====

# Xem messages từ các topics
docker exec stealing-from-paradise-kafka-1 \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic order.created \
    --from-beginning \
    --max-messages 1

docker exec stealing-from-paradise-kafka-1 \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic payment.requested \
    --from-beginning \
    --max-messages 1
```

---

## Troubleshooting - Khắc Phục Lỗi

### API Response Errors

**Lỗi: "Invalid Token"**
```bash
# Nguyên nhân: Token hết hạn hoặc sai
# Giải pháp: Lấy token mới
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@test.local",
    "password": "Password123!"
  }' | jq '.access_token'
```

**Lỗi: "Connection Refused"**
```bash
# Nguyên nhân: Service không chạy
# Giải pháp 1: Kiểm tra Docker
docker-compose ps

# Giải pháp 2: Restart service
docker-compose restart api-gateway

# Giải pháp 3: Xem logs
docker-compose logs api-gateway
```

**Lỗi: "Port Already in Use"**
```bash
# Nguyên nhân: Port 8080 (hoặc khác) bị chiếm
# Giải pháp 1: Tìm process chiếm port
lsof -i :8080

# Giải pháp 2: Kill process
kill -9 <PID>

# Giải pháp 3: Restart Docker
docker-compose down
docker-compose up -d
```

### Database Errors

**Lỗi: "Database Connection Failed"**
```bash
# Kiểm tra PostgreSQL running
docker-compose ps | grep postgres

# Test connection
psql -h localhost -U postgres -d flashsale_platform -c "SELECT 1"

# Restart PostgreSQL
docker-compose restart postgres
sleep 30
docker-compose restart api-gateway
```

### Frontend Issues

**Lỗi: npm install fail**
```bash
# Giải pháp 1: Clear cache
npm cache clean --force

# Giải pháp 2: Xóa node_modules
rm -rf node_modules

# Giải pháp 3: Reinstall
npm install
```

**Lỗi: TypeScript errors**
```bash
# Kiểm tra errors
npm run lint

# Fix errors
npm run lint -- --fix
```

### Stripe Issues

**Lỗi: "Webhook not received"**
```bash
# Kiểm tra Stripe CLI chạy không
# Terminal should show: > Ready! Your webhook signing secret is...

# Restart stripe listen
stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks
```

**Lỗi: "PaymentIntent not created"**
```bash
# 1. Kiểm tra Stripe keys trong .env
grep "STRIPE" .env

# 2. Kiểm tra payment-service logs
docker-compose logs payment-service

# 3. Test Stripe connection
curl https://api.stripe.com/v1/payment_intents \
  -u sk_test_xxxxx: \
  -d amount=1000 \
  -d currency=usd
```

---

## Script Automation - Tự Động Hóa Testing

### Complete E2E Test Script

```bash
#!/bin/bash
# test_e2e.sh - Chạy complete end-to-end test

set -e

echo "=== E2E Test: Complete Order Flow ==="

# 1. Register Seller
echo "1. Registering seller..."
SELLER=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "seller_'$(date +%s)'",
    "email": "seller_'$(date +%s)'@test.local",
    "password": "Password123!",
    "phone": "0901234567",
    "role": "SELLER"
  }')

SELLER_TOKEN=$(echo $SELLER | jq -r '.access_token')
echo "✓ Seller registered: $SELLER_TOKEN"

# 2. Add Product
echo "2. Adding product..."
PRODUCT=$(curl -s -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sku_code": "E2E-TEST-'$(date +%s)'",
    "name": "E2E Test Product",
    "price": 100000,
    "stock": 10,
    "category_id": 1
  }')

PRODUCT_ID=$(echo $PRODUCT | jq '.id')
echo "✓ Product added: $PRODUCT_ID"

# 3. Register Buyer
echo "3. Registering buyer..."
BUYER=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "buyer_'$(date +%s)'",
    "email": "buyer_'$(date +%s)'@test.local",
    "password": "Password123!",
    "phone": "0901234568"
  }')

BUYER_TOKEN=$(echo $BUYER | jq -r '.access_token')
echo "✓ Buyer registered"

# 4. Add to Cart
echo "4. Adding to cart..."
curl -s -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"product_id\": $PRODUCT_ID, \"quantity\": 2}" > /dev/null
echo "✓ Added to cart"

# 5. Create Address
echo "5. Creating address..."
ADDRESS=$(curl -s -X POST http://localhost:8080/api/v1/addresses \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "full_address": "123 Test St",
    "province_id": 79,
    "district_id": 760,
    "phone": "0901234568",
    "is_default": true
  }')

ADDRESS_ID=$(echo $ADDRESS | jq '.id')
echo "✓ Address created: $ADDRESS_ID"

# 6. Checkout
echo "6. Checking out..."
ORDER=$(curl -s -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"address_id\": $ADDRESS_ID,
    \"item_ids\": [\"item_1\"],
    \"use_loyalty_points\": false
  }")

ORDER_ID=$(echo $ORDER | jq '.orders[0].order_id')
echo "✓ Order created: $ORDER_ID"

# 7. Verify Order
echo "7. Verifying order..."
ORDER_STATUS=$(curl -s -X GET http://localhost:8080/api/v1/orders/$ORDER_ID \
  -H "Authorization: Bearer $BUYER_TOKEN" | jq -r '.status')

echo "✓ Order status: $ORDER_STATUS"

echo ""
echo "=== E2E Test Completed Successfully ==="
echo "Order ID: $ORDER_ID"
echo "Status: $ORDER_STATUS"
```

**Chạy script:**
```bash
chmod +x test_e2e.sh
./test_e2e.sh
```

---

## Checklist: Full Testing

- [ ] **Chuẩn Bị**
  - [ ] Docker running (`docker-compose ps`)
  - [ ] Services healthy (`curl http://localhost:8080/actuator/health`)
  - [ ] Eureka shows all services

- [ ] **Unit Tests**
  - [ ] Backend unit tests pass (`mvn test`)
  - [ ] No test failures

- [ ] **Integration Tests**
  - [ ] Integration tests pass (`mvn verify`)
  - [ ] Databases working

- [ ] **Frontend**
  - [ ] TypeScript check passes (`npm run lint`)
  - [ ] Frontend builds (`npm run build`)
  - [ ] Dev servers start

- [ ] **Happy Path**
  - [ ] Register seller
  - [ ] Add product
  - [ ] Register buyer
  - [ ] Add to cart
  - [ ] Checkout
  - [ ] Order created

- [ ] **Payment Saga**
  - [ ] Order created (PENDING status)
  - [ ] Payment gateway initialized
  - [ ] Payment success event received
  - [ ] Order status changed to PAID
  - [ ] Axon saga completed

- [ ] **Manual Checks**
  - [ ] Customer can browse products
  - [ ] Seller can manage products
  - [ ] Admin can see analytics
  - [ ] No errors in browser console
  - [ ] No errors in service logs

---

## Quick Commands - Lệnh Nhanh

```bash
# Docker
docker-compose up -d              # Start all
docker-compose down               # Stop all
docker-compose logs -f service    # View logs
docker-compose ps                 # Status

# Backend Tests
mvn test                          # Unit tests
mvn verify                        # Integration tests
mvn test -Dtest=ClassName        # Specific test

# Frontend
npm run dev                       # Dev server
npm run build                     # Production build
npm run lint                      # TypeScript check

# API Calls
curl http://localhost:8080/actuator/health
curl http://localhost:8761/              # Eureka

# Database
psql -h localhost -U postgres -d flashsale_platform
mongo localhost:27017
redis-cli -h localhost

# Kafka
docker exec stealing-from-paradise-kafka-1 kafka-topics.sh --list --bootstrap-server localhost:9092
docker exec stealing-from-paradise-kafka-1 kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order.created

# Axon Server
open http://localhost:8124/               # Trình duyệt
```

---

## Kết Luận

**Test flow chính:**
1. ✅ Chuẩn bị: `docker-compose up -d` (3 phút)
2. ✅ Unit tests: `mvn test` (2 phút)
3. ✅ Frontend: Xem trình duyệt (5 phút)
4. ✅ Happy path: Seller → Product → Buyer → Order (5 phút)
5. ✅ Payment: Stripe test card (2 phút)

**Tổng thời gian**: ~20 phút để test toàn bộ hệ thống!

**Cần chi tiết hơn?** Xem `docs/PAYMENT_SAGA_STRIPE_SANDBOX_TESTING.md`
