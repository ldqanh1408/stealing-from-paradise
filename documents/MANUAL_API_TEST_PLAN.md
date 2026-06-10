# Backend Manual API Test Plan (Docker Compose Dev Mode)

## Prerequisites
- Stack running: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d`
- Gateway: `http://localhost:8080`

## Dev Accounts (seed data)
| Role | Username | Password | User ID |
|------|----------|----------|---------|
| Buyer | minhhoa | dev123 | 6 |
| Admin | admin | dev123 | - |
| Seller | techworld | dev123 | 1 |
| Seller | fashionhub | dev123 | 2 |
| Seller | gadgetpro | dev123 | 3 |

---

## 1. HEALTH CHECK

### 1.1 Gateway UP
```bash
curl -s http://localhost:8080/actuator/health
```
**Expect:** `{"status":"UP"}`

### 1.2 Eureka services registered
```bash
curl -s http://localhost:8761/eureka/apps
```
**Expect:** XML with 10+ services UP (API-GATEWAY, IDENTITY-SERVICE, PRODUCT-SERVICE, ORDER-SERVICE, PAYMENT-SERVICE, REFUND-SERVICE, FLASHSALE-SERVICE, SEARCH-SERVICE, NOTIFICATION-SERVICE, CHAT-SERVICE)

---

## 2. AUTH (identity-service)

### 2.1 Login các role
```bash
# Buyer
curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"credential":"minhhoa","password":"dev123"}'
# Expect: success=true, accessToken (JWT), role=BUYER

# Seller
curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"credential":"techworld","password":"dev123"}'
# Expect: role=SELLER

# Admin
curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"credential":"admin","password":"dev123"}'
# Expect: role=ADMIN
```

### 2.2 Auth Failures
```bash
# Wrong password → 4xx
curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"credential":"minhhoa","password":"wrong"}'
# Expect: 401

# No JWT on protected route → 401/403
curl -s "http://localhost:8080/api/v1/orders?page=0&size=5"
# Expect: 401 or 403
```

### 2.3 Register
```bash
# Register buyer (default)
curl -s -X POST http://localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{"username":"testuser1","email":"test1@test.com","password":"dev123","fullName":"Test User"}'
# Expect: 201, accessToken, role=BUYER

# Register seller
curl -s -X POST http://localhost:8080/api/v1/auth/register/seller -H 'Content-Type: application/json' -d '{"username":"testseller1","email":"testseller1@test.com","password":"dev123","fullName":"Test Seller"}'
# Expect: 201, accessToken, role=SELLER
```

### 2.4 Refresh & Logout
```bash
# Refresh token
curl -s -X POST http://localhost:8080/api/v1/auth/refresh -H 'Content-Type: application/json' -d '{"refreshToken":"<token>"}'
# Expect: 200, new accessToken

# Logout
curl -s -X POST http://localhost:8080/api/v1/auth/logout -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' -d '{}'
# Expect: 200
```

### 2.5 User Profile & Address CRUD

> **Tokens needed:** `TOKEN=$(login minhhoa)`

```bash
TOKEN="<buyer_token>"

# Get my profile
curl -s http://localhost:8080/api/v1/users/me -H "Authorization: Bearer $TOKEN"
# Expect: 200, body có username, email, fullName

# Update profile
curl -s -X PUT http://localhost:8080/api/v1/users/me -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"fullName":"Minh Hoa Updated"}'
# Expect: 200

# List addresses
curl -s http://localhost:8080/api/v1/users/me/addresses -H "Authorization: Bearer $TOKEN"
# Expect: 200, data array

# Create address
curl -s -X POST http://localhost:8080/api/v1/users/me/addresses -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"fullName":"Test","phoneNumber":"0901234567","provinceId":1,"districtId":1,"wardCode":"00001","streetAddress":"123 Test St","isDefault":false}'
# Expect: 200, data chứa address_id

# Set default address
curl -s -X PUT http://localhost:8080/api/v1/users/me/addresses/<addrId>/default -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Delete address
curl -s -X DELETE http://localhost:8080/api/v1/users/me/addresses/<addrId> -H "Authorization: Bearer $TOKEN"
# Expect: 200

# Change password
curl -s -X POST http://localhost:8080/api/v1/users/me/change-password -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"oldPassword":"dev123","newPassword":"dev1234"}'
# Expect: 200

# Register as seller (upgrade existing user)
curl -s -X POST http://localhost:8080/api/v1/users/me/roles/seller -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Avatar presigned URL
curl -s http://localhost:8080/api/v1/users/me/avatar/presigned-url -H "Authorization: Bearer $TOKEN"
# Expect: 200, presignedUrl
```

### 2.6 Admin User Management

> **Token:** `ADMIN=$(login admin)`

```bash
ADMIN="<admin_token>"

# List users
curl -s "http://localhost:8080/api/v1/admin/users?page=0&size=10" -H "Authorization: Bearer $ADMIN"
# Expect: 200, paginated

# User detail
curl -s http://localhost:8080/api/v1/admin/users/6 -H "Authorization: Bearer $ADMIN"
# Expect: 200

# Lock user
curl -s -X POST http://localhost:8080/api/v1/admin/users/6/lock -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Unlock user
curl -s -X POST http://localhost:8080/api/v1/admin/users/6/unlock -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{}'
# Expect: 200
```

---

## 3. CATALOG (product-service)

### 3.1 Public Product Listing + Detail
```bash
# List products
curl -s "http://localhost:8080/api/v1/products?page=0&size=10"
# Expect: 200, content array, page metadata

# Filter + sort
curl -s "http://localhost:8080/api/v1/products?page=0&size=5&sort=price,asc"
# Expect: 200

# Product detail
curl -s http://localhost:8080/api/v1/products/0b8e36ff-4b51-4d2b-b6c2-9b96373fafc0
# Expect: 200, name=MagSafe Charger 1m

# SKU variant lookup
curl -s http://localhost:8080/api/v1/products/variants/sku/SKU-MAGSAFE
# Expect: 200, data.id, variantCode

# Presigned upload URL
curl -s http://localhost:8080/api/v1/products/0b8e36ff-4b51-4d2b-b6c2-9b96373fafc0/presigned-url -H "Authorization: Bearer $SELLER"
# Expect: 200

# Product images
curl -s http://localhost:8080/api/v1/products/0b8e36ff-4b51-4d2b-b6c2-9b96373fafc0/images
# Expect: 200
```

### 3.2 Categories
```bash
# List all categories (tree)
curl -s http://localhost:8080/api/v1/categories
# Expect: 200, data array with nested children

# Single category
curl -s http://localhost:8080/api/v1/categories/<categoryId>
# Expect: 200
```

### 3.3 Seller Product CRUD

> **Token:** `SELLER=$(login techworld)`

```bash
SELLER="<seller_token>"

# My products
curl -s http://localhost:8080/api/v1/sellers/me/products -H "Authorization: Bearer $SELLER"
# Expect: 200

# Create product
CAT_ID="<leaf_category_uuid>"  # lấy từ GET /categories
curl -s -X POST http://localhost:8080/api/v1/products -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d "{\"name\":\"Manual Test Product\",\"description\":\"Created from manual test\",\"categoryId\":\"$CAT_ID\"}"
# Expect: 201, data.id

# Create variant
PROD_ID="<productId>"
curl -s -X POST "http://localhost:8080/api/v1/seller/products/$PROD_ID/variants" -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{"variantCode":"MNL-TEST-001","variantName":"Manual Test Variant","price":100000,"stockQuantity":50}'
# Expect: 201

# List variants
curl -s "http://localhost:8080/api/v1/seller/products/$PROD_ID/variants" -H "Authorization: Bearer $SELLER"
# Expect: 200

# Update variant
curl -s -X PUT "http://localhost:8080/api/v1/seller/variants/<variantId>" -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{"price":120000}'
# Expect: 200

# Delete variant
curl -s -X DELETE "http://localhost:8080/api/v1/seller/variants/<variantId>" -H "Authorization: Bearer $SELLER"
# Expect: 200

# Upload image
curl -s -X POST "http://localhost:8080/api/v1/products/$PROD_ID/images" -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{"imageUrl":"https://picsum.photos/400/400","isPrimary":true}'
# Expect: 200/201

# Delete image
curl -s -X DELETE http://localhost:8080/api/v1/images/<imageId> -H "Authorization: Bearer $SELLER"
# Expect: 200

# Submit for review
curl -s -X POST "http://localhost:8080/api/v1/seller/products/$PROD_ID/submit" -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Publish
curl -s -X POST "http://localhost:8080/api/v1/seller/products/$PROD_ID/publish" -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Unpublish
curl -s -X POST "http://localhost:8080/api/v1/seller/products/$PROD_ID/unpublish" -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Update product
curl -s -X PUT "http://localhost:8080/api/v1/products/$PROD_ID" -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{"name":"Updated Name"}'
# Expect: 200

# Delete product (seller)
curl -s -X DELETE "http://localhost:8080/api/v1/seller/products/$PROD_ID" -H "Authorization: Bearer $SELLER"
# Expect: 200
```

### 3.4 Admin Product Review

> **Token:** `ADMIN=$(login admin)`

```bash
ADMIN="<admin_token>"

# List pending products
curl -s "http://localhost:8080/api/v1/admin/products/pending?page=0&size=10" -H "Authorization: Bearer $ADMIN"
# Expect: 200

# Approve product
curl -s -X POST "http://localhost:8080/api/v1/admin/products/<productId>/approve" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Reject product
curl -s -X POST "http://localhost:8080/api/v1/admin/products/<productId>/reject" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"reason":"Insufficient description"}'
# Expect: 200
```

### 3.5 Admin Category Management
```bash
# Create category
curl -s -X POST http://localhost:8080/api/v1/admin/categories -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"name":"Test Category","parentId":null}'
# Expect: 200/201

# Update
curl -s -X PUT "http://localhost:8080/api/v1/admin/categories/<catId>" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"name":"Updated Category"}'

# Delete
curl -s -X DELETE "http://localhost:8080/api/v1/admin/categories/<catId>" -H "Authorization: Bearer $ADMIN"
```

### 3.6 Inventory
```bash
# Check inventory
curl -s http://localhost:8080/api/v1/inventory/SKU-MAGSAFE -H "Authorization: Bearer $SELLER"

# Restock
curl -s -X PUT http://localhost:8080/api/v1/inventory/SKU-MAGSAFE/restock -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{"quantity":100,"reason":"Manual restock"}'

# Adjust inventory
curl -s -X POST http://localhost:8080/api/v1/seller/inventory/adjust -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{"skuCode":"SKU-MAGSAFE","quantity":-5,"reason":"Manual adjust"}'

# Inventory logs
curl -s "http://localhost:8080/api/v1/seller/inventory/SKU-MAGSAFE/logs" -H "Authorization: Bearer $SELLER"
```

### 3.7 Cart

> **Token:** `BUYER=$(login minhhoa)`

```bash
BUYER="<buyer_token>"

# Clear cart
curl -s -X DELETE http://localhost:8080/api/v1/cart -H "Authorization: Bearer $BUYER"
# Expect: 200

# Get cart (empty)
curl -s http://localhost:8080/api/v1/cart -H "Authorization: Bearer $BUYER"
# Expect: 200, items=[]

# Add item
curl -s -X POST http://localhost:8080/api/v1/cart/items -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{"variantId":"c5803c7d-2d5c-4178-b579-7266a15ca9ff","quantity":1}'
# Expect: 200

# Update quantity
curl -s -X PUT http://localhost:8080/api/v1/cart/items/c5803c7d-2d5c-4178-b579-7266a15ca9ff -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{"quantity":2}'
# Expect: 200

# Remove item
curl -s -X DELETE http://localhost:8080/api/v1/cart/items/c5803c7d-2d5c-4178-b579-7266a15ca9ff -H "Authorization: Bearer $BUYER"
# Expect: 200

# Validation: invalid variant
curl -s -X POST http://localhost:8080/api/v1/cart/items -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{"variantId":"invalid-uuid","quantity":1}'
# Expect: 4xx

# Inventory reserve
curl -s -X POST http://localhost:8080/api/v1/inventory/c5803c7d-2d5c-4178-b579-7266a15ca9ff/reserve -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{"quantity":1}'
# Expect: 200

# Inventory release
curl -s -X POST http://localhost:8080/api/v1/inventory/reservations/<reservationId>/release -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{}'
# Expect: 200
```

---

## 4. CHECKOUT → ORDER → PAYMENT

> **Token:** `BUYER=$(login minhhoa)`

### 4.1 Checkout
```bash
BUYER="<buyer_token>"

# Clear + add item
curl -s -X DELETE http://localhost:8080/api/v1/cart -H "Authorization: Bearer $BUYER"
curl -s -X POST http://localhost:8080/api/v1/cart/items -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{"variantId":"c5803c7d-2d5c-4178-b579-7266a15ca9ff","quantity":1}'

# Checkout preview
ITEM_ID="6:c5803c7d-2d5c-4178-b579-7266a15ca9ff"
curl -s -X POST http://localhost:8080/api/v1/cart/checkout/preview -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d "{\"itemIds\":[\"$ITEM_ID\"]}"
# Expect: 200, previewToken, totalAmount

# Get address
ADDR_ID=$(curl -s http://localhost:8080/api/v1/users/me/addresses -H "Authorization: Bearer $BUYER" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'][0]['address_id'])")

# Submit (async — order created via Kafka)
curl -s -X POST http://localhost:8080/api/v1/cart/checkout/submit -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d "{\"previewToken\":\"<token>\",\"addressId\":$ADDR_ID}"
# Expect: 200
```

### 4.2 Order Queries
```bash
# My orders
curl -s "http://localhost:8080/api/v1/orders?page=0&size=10" -H "Authorization: Bearer $BUYER"
# Expect: 200, content array

# Single order
curl -s "http://localhost:8080/api/v1/orders/<orderId>" -H "Authorization: Bearer $BUYER"
# Expect: 200, orderId, status, items

# Parent order
curl -s "http://localhost:8080/api/v1/orders/parent/<parentOrderId>" -H "Authorization: Bearer $BUYER"
# Expect: 200, sub-orders array

# Seller orders
curl -s "http://localhost:8080/api/v1/sellers/me/orders?page=0&size=10" -H "Authorization: Bearer $SELLER"
# Expect: 200

# Seller dashboard
curl -s http://localhost:8080/api/v1/sellers/me/dashboard -H "Authorization: Bearer $SELLER"
# Expect: 200, summary stats
```

### 4.3 Order Lifecycle
```bash
# Cancel (buyer)
curl -s -X POST "http://localhost:8080/api/v1/orders/<orderId>/cancel" -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{"reason":"Changed mind"}'
# Expect: 200

# Update tracking (seller)
curl -s -X PUT "http://localhost:8080/api/v1/orders/<orderId>/tracking" -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{"trackingNumber":"TN123456789"}'
# Expect: 200

# Confirm received (buyer)
curl -s -X POST "http://localhost:8080/api/v1/orders/<orderId>/confirm-received" -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Return to sender (with image evidence)
curl -s -X POST "http://localhost:8080/api/v1/orders/<orderId>/return-to-sender" -H "Authorization: Bearer $BUYER" -F "reason=Wrong item" -F "evidence=@/path/to/image.jpg"
# Expect: 200
```

### 4.4 Payment Query
```bash
# Transaction by parent order
curl -s "http://localhost:8080/api/v1/payments/parent-order/<parentOrderId>" -H "Authorization: Bearer $BUYER"
# Expect: 200, transactionId, status, amount
```

---

## 5. STRIPE ONBOARDING & SELLER PAYMENTS

> **Token:** `SELLER=$(login techworld)`

```bash
SELLER="<seller_token>"

# Onboarding status
curl -s http://localhost:8080/api/v1/stripe/onboarding/status -H "Authorization: Bearer $SELLER"
# Expect: 200, stripeAccountId, accountStatus, onboardingStatus, chargesEnabled, payoutsEnabled, detailsSubmitted

# Start onboarding (4xx if already complete)
curl -s -X POST http://localhost:8080/api/v1/stripe/onboarding/start -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{}'
# Expect: 4xx (ALREADY_EXISTS) hoặc 200

# Refresh onboarding link (4xx if already complete)
curl -s -X POST http://localhost:8080/api/v1/stripe/onboarding/refresh-link -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{}'
# Expect: 4xx hoặc 200

# Seller balance
curl -s http://localhost:8080/api/v1/seller/payments/balance -H "Authorization: Bearer $SELLER"
# Expect: 200 (hoặc 500 nếu account chưa complete)

# Transfers history
curl -s "http://localhost:8080/api/v1/seller/payments/transfers?page=0&size=10" -H "Authorization: Bearer $SELLER"
# Expect: 200 (hoặc 500)

# Earnings
curl -s http://localhost:8080/api/v1/seller/payments/earnings -H "Authorization: Bearer $SELLER"
# Expect: 200 (hoặc 500)

# Stripe Express Dashboard link
curl -s http://localhost:8080/api/v1/seller/payments/stripe-dashboard -H "Authorization: Bearer $SELLER"
# Expect: 200, expressDashboardUrl
```

---

## 6. FLASH SALE SERVICE

> **Tokens:** `ADMIN=$(login admin)`, `SELLER=$(login techworld)`, `BUYER=$(login minhhoa)`

```bash
ADMIN="<admin_token>"
SELLER="<seller_token>"
BUYER="<buyer_token>"

# Admin creates session
START=$(date -u -d "-1 hour" +"%Y-%m-%dT%H:%M:%S")
END=$(date -u -d "+1 hour" +"%Y-%m-%dT%H:%M:%S")
curl -s -X POST http://localhost:8080/api/v1/flash-sales -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d "{\"name\":\"Manual Test Flash Sale\",\"startTime\":\"$START\",\"endTime\":\"$END\"}"
# Expect: 200, data.sessionId

SESSION_ID="<sessionId>"

# Activate session
curl -s -X PUT "http://localhost:8080/api/v1/flash-sales/$SESSION_ID" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"status":"ACTIVE"}'
# Expect: 200

# List all sessions
curl -s http://localhost:8080/api/v1/flash-sales -H "Authorization: Bearer $BUYER"
# Expect: 200, data array

# Get active sessions
curl -s http://localhost:8080/api/v1/flash-sales/active -H "Authorization: Bearer $BUYER"
# Expect: 200

# Session detail
curl -s "http://localhost:8080/api/v1/flash-sales/$SESSION_ID" -H "Authorization: Bearer $BUYER"
# Expect: 200, items list

# Seller registers item
curl -s -X POST "http://localhost:8080/api/v1/flash-sales/$SESSION_ID/items" -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -d '{"skuCode":"SKU-MAGSAFE","flashPrice":99000,"flashStock":10,"limitPerUser":2}'
# Expect: 200, data.id

FS_ITEM_ID="<itemId>"

# Admin approves item
curl -s -X POST "http://localhost:8080/api/v1/flash-sales/$SESSION_ID/items/$FS_ITEM_ID/approve" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Admin rejects item
# curl -s -X POST "http://localhost:8080/api/v1/flash-sales/$SESSION_ID/items/$FS_ITEM_ID/reject" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"reason":"Price too high"}'

# Set reminder
curl -s -X POST "http://localhost:8080/api/v1/flash-sales/$SESSION_ID/reminders" -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Remove reminder
curl -s -X DELETE "http://localhost:8080/api/v1/flash-sales/$SESSION_ID/reminders" -H "Authorization: Bearer $BUYER"
# Expect: 200

# Buy flash sale item (cần addressId)
ADDR_ID=$(curl -s http://localhost:8080/api/v1/users/me/addresses -H "Authorization: Bearer $BUYER" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'][0]['address_id'])")
curl -s -X POST "http://localhost:8080/api/v1/flash-sales/$SESSION_ID/buy" -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d "{\"fsItemId\":$FS_ITEM_ID,\"quantity\":1,\"addressId\":$ADDR_ID}"
# Expect: 200, sessionId, fsItemId, totalAmount
```

---

## 7. REFUND FLOW

> **Tokens:** `BUYER=$(login minhhoa)`, `ADMIN=$(login admin)`

```bash
BUYER="<buyer_token>"
ADMIN="<admin_token>"

# Request refund on an order
curl -s -X POST "http://localhost:8080/api/v1/orders/<orderId>/refunds" -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{"reason":"Item damaged","items":[{"orderItemId":<itemId>,"quantity":1,"itemReason":"damaged"}],"evidenceImages":[]}'
# Expect: 200/201, refundId, status=PENDING

# List refunds per order
curl -s "http://localhost:8080/api/v1/orders/<orderId>/refunds" -H "Authorization: Bearer $BUYER"
# Expect: 200

# Refund detail
curl -s "http://localhost:8080/api/v1/orders/<orderId>/refunds/<refundId>" -H "Authorization: Bearer $BUYER"
# Expect: 200

# Parent order refund
curl -s "http://localhost:8080/api/v1/orders/parent/<parentOrderId>/refund" -H "Authorization: Bearer $BUYER"
# Expect: 200

# Parent order partial refund
curl -s -X POST "http://localhost:8080/api/v1/orders/parent/<parentOrderId>/refunds/partial" -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{"refundAmount":50000,"reason":"Partial refund"}'
# Expect: 200

# List all refunds (paginated)
curl -s "http://localhost:8080/api/v1/orders/refunds?page=0&size=10" -H "Authorization: Bearer $BUYER"
# Expect: 200

# Presigned URL for evidence upload
curl -s "http://localhost:8080/api/v1/orders/<orderId>/refunds/presigned-url" -H "Authorization: Bearer $BUYER"
# Expect: 200

# Admin: list all refunds
curl -s "http://localhost:8080/api/v1/admin/refunds?page=0&size=10" -H "Authorization: Bearer $ADMIN"
# Expect: 200

# Admin: refund detail
curl -s "http://localhost:8080/api/v1/admin/refunds/<refundId>" -H "Authorization: Bearer $ADMIN"
# Expect: 200

# Admin: approve refund
curl -s -X POST "http://localhost:8080/api/v1/admin/refunds/<refundId>/approve" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Admin: reject refund
curl -s -X POST "http://localhost:8080/api/v1/admin/refunds/<refundId>/reject" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"reason":"Not eligible"}'
# Expect: 200
```

---

## 8. SEARCH SERVICE

```bash
BUYER="<buyer_token>"
ADMIN="<admin_token>"

# Search products
curl -s "http://localhost:8080/api/v1/search/products?q=MagSafe&page=0&size=10" -H "Authorization: Bearer $BUYER"
# Expect: 200

# Search suggestions
curl -s "http://localhost:8080/api/v1/search/products/suggest?q=Mag" -H "Authorization: Bearer $BUYER"
# Expect: 200

# Reindex trigger (admin)
curl -s -X POST http://localhost:8080/api/v1/search/reindex -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{}'
# Expect: 200 or 409 (already running)

# Reindex status
curl -s http://localhost:8080/api/v1/search/reindex/status -H "Authorization: Bearer $ADMIN"
# Expect: 200, data.status
```

---

## 9. NOTIFICATION SERVICE

```bash
BUYER="<buyer_token>"

# Unread count
curl -s http://localhost:8080/api/v1/notifications/unread-count -H "Authorization: Bearer $BUYER"
# Expect: 200, unread_count

# History
curl -s http://localhost:8080/api/v1/notifications/history -H "Authorization: Bearer $BUYER"
# Expect: 200, JSON array

# Mark all read
curl -s -X PUT http://localhost:8080/api/v1/notifications/read-all -H "Authorization: Bearer $BUYER" -H 'Content-Type: application/json' -d '{}'
# Expect: 200

# Verify unread = 0
curl -s http://localhost:8080/api/v1/notifications/unread-count -H "Authorization: Bearer $BUYER"
# Expect: unread_count=0

# SSE stream (test for ~10s)
timeout 10 curl -s -N http://localhost:8080/api/v1/notifications/stream -H "Authorization: Bearer $BUYER" -H "Accept: text/event-stream"
# Expect: SSE events hoặc timeout (nếu không có notification mới)
```

---

## 10. AI CHAT SERVICE

> **Important:** Chat service requires BOTH `Authorization: Bearer <token>` AND `X-User-Id: <userId>` headers.

```bash
BUYER="<buyer_token>"
USER_ID=6

# Get suggest prompts
curl -s http://localhost:8080/api/ai/suggest -H "Authorization: Bearer $BUYER" -H "X-User-Id: $USER_ID"
# Expect: 200, data array

# Create session
SESSION=$(curl -s -X POST http://localhost:8080/api/ai/sessions -H "Authorization: Bearer $BUYER" -H "X-User-Id: $USER_ID" -H 'Content-Type: application/json' -d '{}')
echo $SESSION
SESSION_ID=$(echo $SESSION | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
# Expect: 201, data.id

# List sessions
curl -s http://localhost:8080/api/ai/sessions -H "Authorization: Bearer $BUYER" -H "X-User-Id: $USER_ID"
# Expect: 200, data array

# Chat message (SSE stream — needs LLM API reachable)
curl -s -X POST http://localhost:8080/api/ai/chat -H "Authorization: Bearer $BUYER" -H "X-User-Id: $USER_ID" -H 'Content-Type: application/json' -H 'Accept: text/event-stream' --max-time 30 -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"Hello, what products do you recommend?\"}"
# Expect: SSE events with AI response

# Chat history
curl -s "http://localhost:8080/api/ai/chat/history?sessionId=$SESSION_ID" -H "Authorization: Bearer $BUYER" -H "X-User-Id: $USER_ID"
# Expect: 200, messages array

# Confirm action (human-in-the-loop)
curl -s -X POST http://localhost:8080/api/ai/confirm -H "Authorization: Bearer $BUYER" -H "X-User-Id: $USER_ID" -H 'Content-Type: application/json' -d '{"sessionId":"<id>","confirmationId":"<id>","confirmed":true}'
# Expect: 200

# Close session
curl -s -X DELETE "http://localhost:8080/api/ai/sessions/$SESSION_ID" -H "Authorization: Bearer $BUYER" -H "X-User-Id: $USER_ID"
# Expect: 200
```

---

## 11. END-TO-END BUSINESS FLOWS (Use Cases)

> **Mục đích:** kiểm thử trực tiếp các use case / business flow trên stack Docker `docker-compose.dev.yml` đang chạy. Mỗi UC nối nhiều endpoint thành 1 luồng thực tế, phản ánh đúng những gì JUnit E2E suite (`backend/e2e-tests`) thực thi — bạn có thể chạy tay qua bash để debug nhanh, rồi `mvn -pl e2e-tests test -Pe2e` để regression.

### Pre-flight (chạy 1 lần / shell)
```bash
GATEWAY="http://localhost:8080"
# whsec_… phải khớp STRIPE_WEBHOOK_SECRET trong .env (dev default)
WEBHOOK_SECRET="whsec_9036236865171c8dd43b2c376f96d9847980b59fc9eef44c16ccb2ca0feb7268"

login() {
  curl -s -X POST $GATEWAY/api/v1/auth/login -H 'Content-Type: application/json' \
    -d "{\"credential\":\"$1\",\"password\":\"dev123\"}" \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])"
}
BUYER=$(login minhhoa);  SELLER=$(login techworld);  ADMIN=$(login admin)

# Poll một endpoint cho tới khi expression trả về expected value (timeout ~90s)
poll_status() {  # $1=url $2=token $3=python-expr  $4=expected
  for i in $(seq 1 45); do
    val=$(curl -s "$1" -H "Authorization: Bearer $2" \
          | python3 -c "import sys,json;d=json.load(sys.stdin);$3" 2>/dev/null)
    [ "$val" = "$4" ] && { echo "  OK after ${i}x2s: $val"; return 0; }
    sleep 2
  done
  echo "  TIMEOUT — expected $4, last=$val"; return 1
}

# Ký HMAC-SHA256 + POST tới gateway, dùng đúng format Stripe-Signature
send_stripe_webhook() {  # $1=payload-json
  PAYLOAD="$1";  TS=$(date +%s)
  SIG=$(python3 -c "import hmac,hashlib,os;print(hmac.new(os.environ['WEBHOOK_SECRET'].encode(),f\"{os.environ['TS']}.{os.environ['PAYLOAD']}\".encode(),hashlib.sha256).hexdigest())" \
        WEBHOOK_SECRET="$WEBHOOK_SECRET" TS="$TS" PAYLOAD="$PAYLOAD")
  curl -s -o /dev/null -w "  webhook http=%{http_code}\n" -X POST $GATEWAY/api/v1/stripe/webhooks \
       -H "Content-Type: application/json" \
       -H "Stripe-Signature: t=$TS,v1=$SIG" \
       --data-binary "$PAYLOAD"
}
```

> **Async note:** mọi UC có dấu ✓ (Async) chạy qua Kafka + Axon saga — sau khi trigger phải `poll_status` cho đến khi đạt trạng thái mong đợi (đừng assert ngay).

---

### UC-11.1 — Checkout → Payment SUCCESS → Sub-orders PAID  (mirror E2E-A04 happy path)
**Phạm vi:** product-service (cart/preview/submit) → Kafka `checkout.submitted` → order-service (Axon saga + sub-orders) → payment-service (Stripe PaymentIntent) → Stripe webhook → order PAID.

```bash
VARIANT="c5803c7d-2d5c-4178-b579-7266a15ca9ff";  CUST_ID=6

curl -s -X DELETE $GATEWAY/api/v1/cart -H "Authorization: Bearer $BUYER" >/dev/null
curl -s -X POST   $GATEWAY/api/v1/cart/items -H "Authorization: Bearer $BUYER" \
     -H 'Content-Type: application/json' -d "{\"variantId\":\"$VARIANT\",\"quantity\":1}" >/dev/null

PREVIEW_TOKEN=$(curl -s -X POST $GATEWAY/api/v1/cart/checkout/preview -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' -d "{\"itemIds\":[\"$CUST_ID:$VARIANT\"]}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['previewToken'])")
ADDR_ID=$(curl -s $GATEWAY/api/v1/users/me/addresses -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['address_id'])")

# Snapshot max parentOrderId BEFORE submit
PRE_MAX=$(curl -s "$GATEWAY/api/v1/orders?page=0&size=100" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;c=json.load(sys.stdin).get('content',[]);print(max([o.get('parentOrderId') or 0 for o in c]+[0]))")

curl -s -X POST $GATEWAY/api/v1/cart/checkout/submit -H "Authorization: Bearer $BUYER" \
     -H 'Content-Type: application/json' \
     -d "{\"previewToken\":\"$PREVIEW_TOKEN\",\"addressId\":$ADDR_ID}" >/dev/null

# Đợi parent order mới xuất hiện
PARENT_ID=""
for i in $(seq 1 45); do
  PARENT_ID=$(curl -s "$GATEWAY/api/v1/orders?page=0&size=100" -H "Authorization: Bearer $BUYER" \
    | python3 -c "import sys,json,os;m=int(os.environ['PRE_MAX']);c=json.load(sys.stdin).get('content',[]);
n=[o['parentOrderId'] for o in c if (o.get('parentOrderId') or 0)>m];print(n[0] if n else '')" PRE_MAX=$PRE_MAX)
  [ -n "$PARENT_ID" ] && break;  sleep 2
done
echo "parentOrderId=$PARENT_ID"

# Chờ transaction PENDING (payment-service đã tạo PI Stripe)
poll_status "$GATEWAY/api/v1/payments/parent-order/$PARENT_ID" "$BUYER" "print(d['data']['status'])" "PENDING"

# Forge payment_intent.succeeded
PAYLOAD="{\"id\":\"evt_uc111_$(date +%s)\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_uc111\",\"metadata\":{\"parentOrderId\":\"$PARENT_ID\"},\"amount\":99000,\"currency\":\"vnd\"}}}"
send_stripe_webhook "$PAYLOAD"

# Verify cuối: transaction SUCCESS + tất cả sub-orders PAID
poll_status "$GATEWAY/api/v1/payments/parent-order/$PARENT_ID" "$BUYER" "print(d['data']['status'])" "SUCCESS"
poll_status "$GATEWAY/api/v1/orders/parent/$PARENT_ID" "$BUYER" \
  "print('PASS' if all(s['status']=='PAID' for s in d['data']['subOrders']) else 'WAIT')" "PASS"
```
**Pass criteria:** parentOrder mới được tạo · tất cả sub-orders PENDING → PAID · transaction PENDING → SUCCESS · gọi lại `/payments/parent-order/$PARENT_ID` 2 lần liên tiếp trả về cùng `transactionId` (idempotent).

---

### UC-11.2 — Payment FAILED → Sub-orders CANCELLED  (E2E-A04 failure path)
```bash
# Chạy UC-11.1 tới PENDING (đừng forge succeeded). Sau đó:
PAYLOAD="{\"id\":\"evt_uc112_$(date +%s)\",\"type\":\"payment_intent.payment_failed\",\"data\":{\"object\":{\"id\":\"pi_uc112\",\"metadata\":{\"parentOrderId\":\"$PARENT_ID\"},\"last_payment_error\":{\"message\":\"card_declined\"}}}}"
send_stripe_webhook "$PAYLOAD"

poll_status "$GATEWAY/api/v1/payments/parent-order/$PARENT_ID" "$BUYER" "print(d['data']['status'])" "FAILED"
poll_status "$GATEWAY/api/v1/orders/parent/$PARENT_ID" "$BUYER" \
  "print('PASS' if all(s['status']=='CANCELLED' for s in d['data']['subOrders']) else 'WAIT')" "PASS"
```
**Pass criteria:** Axon saga compensates → mọi sub-order CANCELLED · transaction FAILED.

---

### UC-11.3 — Buyer hủy PENDING order  (E2E-A04 buyer cancel)
```bash
# Sau UC-11.1 → PENDING:
for OID in $(curl -s $GATEWAY/api/v1/orders/parent/$PARENT_ID -H "Authorization: Bearer $BUYER" \
              | python3 -c "import sys,json;print(' '.join(str(s['orderId']) for s in json.load(sys.stdin)['data']['subOrders']))"); do
  curl -s -X POST $GATEWAY/api/v1/orders/$OID/cancel -H "Authorization: Bearer $BUYER" \
       -H 'Content-Type: application/json' -d '{"reason":"Đổi ý"}' >/dev/null
done

poll_status "$GATEWAY/api/v1/payments/parent-order/$PARENT_ID" "$BUYER" "print(d['data']['status'])" "CANCELLED"
```
**Pass criteria:** order.cancelled → payment-service cancel PaymentIntent + transaction CANCELLED.

---

### UC-11.4 — Fulfillment + Partial Refund  (E2E-A05)
**Phạm vi:** PAID → seller ships → buyer confirm → refund request → admin approve (Kafka request-reply giữa order-service ↔ refund-service).
```bash
# Pre-req: UC-11.1 đã PAID
SUB=$(curl -s $GATEWAY/api/v1/orders/parent/$PARENT_ID -H "Authorization: Bearer $BUYER" \
      | python3 -c "import sys,json;s=json.load(sys.stdin)['data']['subOrders'][0];print(s['orderId'],s['sellerId'])")
ORDER_ID=$(echo $SUB|cut -d' ' -f1);  SID=$(echo $SUB|cut -d' ' -f2)
# Map sellerId 1=techworld, 2=fashionhub, 3=gadgetpro, 4=homeliving, 5=sportoutdoor
case $SID in 1) SELLER=$(login techworld);; 2) SELLER=$(login fashionhub);; 3) SELLER=$(login gadgetpro);;
              4) SELLER=$(login homeliving);; 5) SELLER=$(login sportoutdoor);; esac

curl -s -X PUT $GATEWAY/api/v1/orders/$ORDER_ID/tracking -H "Authorization: Bearer $SELLER" \
     -H 'Content-Type: application/json' -d '{"trackingNumber":"MANUAL-UC114-001"}' >/dev/null
poll_status "$GATEWAY/api/v1/orders/$ORDER_ID" "$BUYER" "print(d['data']['status'])" "SHIPPING"

curl -s -X POST $GATEWAY/api/v1/orders/$ORDER_ID/confirm-received -H "Authorization: Bearer $BUYER" \
     -H 'Content-Type: application/json' -d '{}' >/dev/null
poll_status "$GATEWAY/api/v1/orders/$ORDER_ID" "$BUYER" "print(d['data']['status'])" "DELIVERED"

ITEM_ID=$(curl -s $GATEWAY/api/v1/orders/$ORDER_ID -H "Authorization: Bearer $BUYER" \
          | python3 -c "import sys,json;i=json.load(sys.stdin)['data']['items'][0];print(i.get('orderItemId') or i['id'])")
REFUND_ID=$(curl -s -X POST $GATEWAY/api/v1/orders/$ORDER_ID/refunds -H "Authorization: Bearer $BUYER" \
     -H 'Content-Type: application/json' \
     -d "{\"reason\":\"Hàng lỗi\",\"items\":[{\"orderItemId\":$ITEM_ID,\"quantity\":1,\"itemReason\":\"damaged\"}],\"evidenceImages\":[]}" \
     | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['data'].get('refundId') or d['data'].get('id'))")

curl -s -X POST $GATEWAY/api/v1/admin/refunds/$REFUND_ID/approve -H "Authorization: Bearer $ADMIN" \
     -H 'Content-Type: application/json' -d '{}' >/dev/null
poll_status "$GATEWAY/api/v1/orders/$ORDER_ID/refunds/$REFUND_ID" "$BUYER" "print(d['data']['status'])" "APPROVED"
```
**Pass criteria:** order chuyển PAID → SHIPPING → DELIVERED · refund record xuất hiện qua Kafka request-reply · admin approve → status APPROVED.

---

### UC-11.5 — Flash Sale Lifecycle  (E2E-A06, UC-FLASHSALE-001/002/003/005/006)
```bash
START=$(date -u -d "-1 hour" +"%Y-%m-%dT%H:%M:%S");  END=$(date -u -d "+1 hour" +"%Y-%m-%dT%H:%M:%S")

SES_ID=$(curl -s -X POST $GATEWAY/api/v1/flash-sales -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Manual UC-11.5\",\"startTime\":\"$START\",\"endTime\":\"$END\"}" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d.get('sessionId') or d.get('id'))")
curl -s -X PUT $GATEWAY/api/v1/flash-sales/$SES_ID -H "Authorization: Bearer $ADMIN" \
     -H 'Content-Type: application/json' -d '{"status":"ACTIVE"}' >/dev/null

FS_ITEM=$(curl -s -X POST $GATEWAY/api/v1/flash-sales/$SES_ID/items -H "Authorization: Bearer $SELLER" \
  -H 'Content-Type: application/json' \
  -d '{"skuCode":"SKU-MAGSAFE","flashPrice":99000,"flashStock":10,"limitPerUser":2}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")

curl -s -X POST $GATEWAY/api/v1/flash-sales/$SES_ID/items/$FS_ITEM/approve -H "Authorization: Bearer $ADMIN" \
     -H 'Content-Type: application/json' -d '{}' >/dev/null
curl -s -X POST $GATEWAY/api/v1/flash-sales/$SES_ID/reminders -H "Authorization: Bearer $BUYER" \
     -H 'Content-Type: application/json' -d '{}' >/dev/null

ADDR_ID=$(curl -s $GATEWAY/api/v1/users/me/addresses -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['address_id'])")
curl -s -X POST $GATEWAY/api/v1/flash-sales/$SES_ID/buy -H "Authorization: Bearer $BUYER" \
     -H 'Content-Type: application/json' \
     -d "{\"fsItemId\":$FS_ITEM,\"quantity\":1,\"addressId\":$ADDR_ID}"

# Verify: order mới xuất hiện trong /orders với totalAmount = 99000
sleep 5
curl -s "$GATEWAY/api/v1/orders?page=0&size=3&sort=createdAt,desc" -H "Authorization: Bearer $BUYER"
```
**Pass criteria:** session active · seller item APPROVED · Redis Lua decrement stock (flash_stock giảm) · `flash_sale.buy` Kafka event → parent order tạo qua checkout saga.

---

### UC-11.6 — Stripe Connect Onboarding  (E2E-A16, UC-PAYMENT-008)
```bash
RAND=$(date +%s);  NEW="ucseller$RAND"
NS=$(curl -s -X POST $GATEWAY/api/v1/auth/register/seller -H 'Content-Type: application/json' \
     -d "{\"username\":\"$NEW\",\"email\":\"$NEW@test.com\",\"password\":\"dev123\",\"fullName\":\"UC Seller\"}" \
     | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

curl -s -X POST $GATEWAY/api/v1/stripe/onboarding/start -H "Authorization: Bearer $NS" \
     -H 'Content-Type: application/json' -d '{}'         # expect 200 + onboardingUrl
curl -s $GATEWAY/api/v1/stripe/onboarding/status -H "Authorization: Bearer $NS"    # PENDING
curl -s -X POST $GATEWAY/api/v1/stripe/onboarding/refresh-link -H "Authorization: Bearer $NS" \
     -H 'Content-Type: application/json' -d '{}'         # expect 200 + URL mới

# Forge account.updated để mô phỏng seller hoàn tất onboarding
ACCT=$(curl -s $GATEWAY/api/v1/stripe/onboarding/status -H "Authorization: Bearer $NS" \
       | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['stripeAccountId'])")
PAYLOAD="{\"id\":\"evt_acct_$(date +%s)\",\"type\":\"account.updated\",\"data\":{\"object\":{\"id\":\"$ACCT\",\"charges_enabled\":true,\"payouts_enabled\":true,\"details_submitted\":true}}}"
send_stripe_webhook "$PAYLOAD"

poll_status "$GATEWAY/api/v1/stripe/onboarding/status" "$NS" "print(d['data']['chargesEnabled'])" "True"
```
**Pass criteria:** SELLER_STRIPE_ACCOUNTS có record mới · sau webhook, chargesEnabled=true & onboardingStatus=COMPLETE.

---

### UC-11.7 — Payment Idempotency  (E2E-A13)
```bash
# Pre-req: UC-11.1 đã đạt PENDING (hoặc SUCCESS), $PARENT_ID set sẵn
TX1=$(curl -s $GATEWAY/api/v1/payments/parent-order/$PARENT_ID -H "Authorization: Bearer $BUYER" \
      | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['transactionId'])")
sleep 5
TX2=$(curl -s $GATEWAY/api/v1/payments/parent-order/$PARENT_ID -H "Authorization: Bearer $BUYER" \
      | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['transactionId'])")
[ "$TX1" = "$TX2" ] && echo "PASS idempotent ($TX1)" || echo "FAIL tx changed $TX1 → $TX2"
```

---

### UC-11.8 — Multi-Seller Cart → 1 Parent / N Sub-orders / 1 Transaction  (E2E-A13)
```bash
V1="c5803c7d-2d5c-4178-b579-7266a15ca9ff"
V2="<variantId của product thuộc seller khác — lấy từ /api/v1/products>"
curl -s -X DELETE $GATEWAY/api/v1/cart -H "Authorization: Bearer $BUYER" >/dev/null
for V in $V1 $V2; do
  curl -s -X POST $GATEWAY/api/v1/cart/items -H "Authorization: Bearer $BUYER" \
       -H 'Content-Type: application/json' -d "{\"variantId\":\"$V\",\"quantity\":1}" >/dev/null
done

CUST_ID=6
PREVIEW_TOKEN=$(curl -s -X POST $GATEWAY/api/v1/cart/checkout/preview -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' -d "{\"itemIds\":[\"$CUST_ID:$V1\",\"$CUST_ID:$V2\"]}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['previewToken'])")
# submit như UC-11.1, lấy $PARENT_ID …

curl -s $GATEWAY/api/v1/orders/parent/$PARENT_ID -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('subOrders=',len(d['subOrders']),'sellers=',{s['sellerId'] for s in d['subOrders']})"
curl -s $GATEWAY/api/v1/payments/parent-order/$PARENT_ID -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('tx=',d['transactionId'],'amount=',d.get('amount') or d.get('totalAmount'))"
```
**Pass criteria:** ≥2 sub-orders với sellerId khác nhau · đúng 1 transactionId · amount = sum giá cả 2 variants.

---

### UC-11.9 — charge.refunded Webhook  (E2E-A13)
```bash
# Pre-req: UC-11.1 đã SUCCESS
PAYLOAD="{\"id\":\"evt_ref_$(date +%s)\",\"type\":\"charge.refunded\",\"data\":{\"object\":{\"id\":\"ch_uc119\",\"metadata\":{\"parentOrderId\":\"$PARENT_ID\"},\"amount_refunded\":50000}}}"
send_stripe_webhook "$PAYLOAD"     # expect 200; Kafka refund.charge_refunded published
```

---

### UC-11.10 — Seller Transfer Webhooks  (E2E-A14)
```bash
TR="tr_uc1110_$(date +%s)"
PAYLOAD="{\"id\":\"evt_tr_$(date +%s)\",\"type\":\"transfer.created\",\"data\":{\"object\":{\"id\":\"$TR\",\"destination\":\"acct_techworld\",\"amount\":85000,\"metadata\":{\"orderId\":\"$ORDER_ID\"}}}}"
send_stripe_webhook "$PAYLOAD"

curl -s "$GATEWAY/api/v1/seller/payments/transfers?page=0&size=10" -H "Authorization: Bearer $SELLER"
# Expect: row mới với id=$TR, status=PENDING/CREATED

PAYLOAD2="{\"id\":\"evt_tr2_$(date +%s)\",\"type\":\"transfer.reversed\",\"data\":{\"object\":{\"id\":\"$TR\",\"reversed\":true}}}"
send_stripe_webhook "$PAYLOAD2"
# Re-query → status REVERSED
```

---

### UC-11.11 — Webhook Signature Verification (negative)  (E2E-A15)
```bash
# Unsigned
curl -s -o /dev/null -w "unsigned http=%{http_code}\n" -X POST $GATEWAY/api/v1/stripe/webhooks \
     -H 'Content-Type: application/json' -d '{"type":"payment_intent.succeeded"}'
# Expect: 400/401

# Sai secret
TS=$(date +%s);  BODY='{"type":"payment_intent.succeeded"}'
BAD=$(python3 -c "import hmac,hashlib;print(hmac.new(b'whsec_WRONG',f'$TS.$BODY'.encode(),hashlib.sha256).hexdigest())")
curl -s -o /dev/null -w "wrong-sig http=%{http_code}\n" -X POST $GATEWAY/api/v1/stripe/webhooks \
     -H "Stripe-Signature: t=$TS,v1=$BAD" -H 'Content-Type: application/json' -d "$BODY"
# Expect: 400/401
```

---

### UC-11.12 — Product Publish → Search Reindex (Kafka cross-service)
```bash
CAT=$(curl -s $GATEWAY/api/v1/categories \
       | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];leaves=[c for c in d if not c.get('children')];print(leaves[0]['id'])")
NAME="Manual Search UC $(date +%s)"
PID=$(curl -s -X POST $GATEWAY/api/v1/products -H "Authorization: Bearer $SELLER" \
       -H 'Content-Type: application/json' \
       -d "{\"name\":\"$NAME\",\"description\":\"Search reindex test\",\"categoryId\":\"$CAT\"}" \
       | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
curl -s -X POST $GATEWAY/api/v1/seller/products/$PID/variants -H "Authorization: Bearer $SELLER" \
     -H 'Content-Type: application/json' \
     -d '{"variantCode":"MNL-SEARCH-001","variantName":"v1","price":100000,"stockQuantity":10}' >/dev/null
curl -s -X POST $GATEWAY/api/v1/seller/products/$PID/submit  -H "Authorization: Bearer $SELLER" -d '{}' -H 'Content-Type: application/json' >/dev/null
curl -s -X POST $GATEWAY/api/v1/admin/products/$PID/approve  -H "Authorization: Bearer $ADMIN"  -d '{}' -H 'Content-Type: application/json' >/dev/null
curl -s -X POST $GATEWAY/api/v1/seller/products/$PID/publish -H "Authorization: Bearer $SELLER" -d '{}' -H 'Content-Type: application/json' >/dev/null

# Poll search index (search-service consume product.published từ Kafka)
ENC=$(python3 -c "import urllib.parse,os;print(urllib.parse.quote(os.environ['NAME']))" NAME="$NAME")
poll_status "$GATEWAY/api/v1/search/products?q=$ENC&page=0&size=5" "$BUYER" \
  "print('PASS' if any('Manual Search UC' in p.get('name','') for p in d['data']['content']) else 'WAIT')" "PASS"
```
**Pass criteria:** sau publish ≤90s, `/search/products?q=...` trả về sản phẩm mới (ES_PRODUCTS_INDEX updated qua Kafka `product.published`).

---

### UC-11.13 — Order Status Change → Notification SSE
```bash
BEFORE=$(curl -s $GATEWAY/api/v1/notifications/unread-count -H "Authorization: Bearer $BUYER" \
         | python3 -c "import sys,json;print(json.load(sys.stdin)['unread_count'])")

# (chạy UC-11.1 full flow để trigger order.paid / payment.success → notification consumer)

sleep 8
AFTER=$(curl -s $GATEWAY/api/v1/notifications/unread-count -H "Authorization: Bearer $BUYER" \
        | python3 -c "import sys,json;print(json.load(sys.stdin)['unread_count'])")
[ "$AFTER" -gt "$BEFORE" ] && echo "PASS unread $BEFORE→$AFTER" || echo "FAIL no notification"

# Optional: subscribe SSE 10s rồi chạy lại UC-11.1 ở shell khác
timeout 15 curl -s -N $GATEWAY/api/v1/notifications/stream -H "Authorization: Bearer $BUYER" -H "Accept: text/event-stream"
```

---

## 12. Business-Flow Checklist

| UC | Flow | Services chạm tới | Async | Pass? |
|----|------|-------------------|:-----:|:-----:|
| 11.1 | Checkout → Payment SUCCESS → PAID | product, order(Axon), payment(Stripe) | ✓ | ⬜ |
| 11.2 | Checkout → Payment FAILED → CANCELLED | order(Axon saga compensate), payment | ✓ | ⬜ |
| 11.3 | Buyer huỷ PENDING → transaction CANCELLED | order, payment | ✓ | ⬜ |
| 11.4 | Paid → Ship → Deliver → Refund → Admin approve | order, refund (Kafka request-reply) | ✓ | ⬜ |
| 11.5 | Flash sale: admin create → seller item → approve → buy | flashsale (Redis Lua), order | ✓ | ⬜ |
| 11.6 | Stripe Connect onboarding + account.updated | payment | — | ⬜ |
| 11.7 | Payment idempotency (no duplicate tx) | payment | — | ⬜ |
| 11.8 | Multi-seller cart → 1 parent / N sub / 1 tx | order, payment | ✓ | ⬜ |
| 11.9 | charge.refunded webhook accepted | payment | — | ⬜ |
| 11.10 | transfer.created / transfer.reversed | payment | — | ⬜ |
| 11.11 | Webhook signature rejection (negative) | payment (gateway) | — | ⬜ |
| 11.12 | Product publish → search reindex | product → kafka → search (ES) | ✓ | ⬜ |
| 11.13 | Order status → notification SSE | notification (kafka consumer) | ✓ | ⬜ |

### Cross-reference với JUnit E2E
| UC | E2E test class · method |
|----|-------------------------|
| 11.1–11.3 | `A04OrderPaymentE2eTest` |
| 11.4 | `A05RefundFlowE2eTest.fulfillmentAndPartialRefund` |
| 11.5 | `A06FlashSaleE2eTest.flashSaleLifecycle` |
| 11.6 | `A16StripeOnboardingE2eTest.fullOnboardingFlow` |
| 11.7 / 11.8 / 11.9 | `A13PaymentCoreE2eTest` |
| 11.10 | `A14SellerTransferE2eTest` |
| 11.11 | `A15WebhookHandlersE2eTest` |
| 11.12 / 11.13 | (manual-only — không có JUnit tương đương) |

---

## Verification Checklist (per-endpoint)

| # | Flow | Endpoints | Pass? |
|---|------|-----------|-------|
| 1 | Health | 2 (gateway + eureka) | ⬜ |
| 2 | Login | 3 (buyer/seller/admin) | ⬜ |
| 3 | Auth failures | 2 (wrong pwd, no JWT) | ⬜ |
| 4 | Register | 2 (buyer + seller) | ⬜ |
| 5 | Refresh + Logout | 2 | ⬜ |
| 6 | Profile + Avatar | 3 | ⬜ |
| 7 | Address CRUD | 4 (list/create/default/delete) | ⬜ |
| 8 | Change password | 1 | ⬜ |
| 9 | Register as seller | 1 | ⬜ |
| 10 | Admin users | 4 (list/detail/lock/unlock) | ⬜ |
| 11 | Product listing | 3 (list/filter/detail) | ⬜ |
| 12 | SKU + Images | 3 | ⬜ |
| 13 | Categories | 2 (list/detail) | ⬜ |
| 14 | Seller product CRUD | 8 (create/update/variant/image/publish) | ⬜ |
| 15 | Admin product review | 3 (pending/approve/reject) | ⬜ |
| 16 | Admin categories | 3 (create/update/delete) | ⬜ |
| 17 | Inventory | 4 (check/restock/adjust/logs) | ⬜ |
| 18 | Cart | 8 (clear/get/add/update/remove/validate/reserve/release) | ⬜ |
| 19 | Checkout | 2 (preview/submit) | ⬜ |
| 20 | Order queries | 5 (list/detail/parent/seller/dashboard) | ⬜ |
| 21 | Order lifecycle | 4 (cancel/tracking/received/return) | ⬜ |
| 22 | Payment query | 1 | ⬜ |
| 23 | Stripe onboarding | 3 (status/start/refresh) | ⬜ |
| 24 | Seller payments | 4 (balance/transfers/earnings/dashboard) | ⬜ |
| 25 | Flash sale | 8 (session/active/detail/item/approve/reminder/buy) | ⬜ |
| 26 | Refund | 8 (request/list/detail/parent/partial/admin/reject) | ⬜ |
| 27 | Search | 4 (query/suggest/reindex/status) | ⬜ |
| 28 | Notification | 4 (count/history/read/SSE) | ⬜ |
| 29 | AI Chat | 6 (suggest/session/list/chat/history/close) | ⬜ |
| **TOTAL** | | **~100** | |
