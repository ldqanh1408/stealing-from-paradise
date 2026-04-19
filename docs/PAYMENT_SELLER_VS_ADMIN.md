# 🔐 Payment Flow: Seller vs Admin Roles

**Ngày**: 2026-04-19  
**Mục Đích**: Giải thích vai trò Seller & Admin trong payment flow

---

## 📊 Payment Flow Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    BUYER CHECKOUT                           │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│              PLATFORM (ADMIN) SETUP                         │
│  - Setup Stripe platform account                            │
│  - Configure webhook secret (whsec_test_xxxxx)             │
│  - Set fee percentage (5%)                                  │
│  - Verify all sellers onboarded                            │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│              PAYMENT PROCESSING                             │
│  1. Buyer pays → Stripe (PaymentIntent)                    │
│  2. Platform receives money                                 │
│  3. Platform deducts fee: 200,000 - 10,000 = 190,000      │
│  4. Platform creates Transfer to Seller account            │
│  5. Seller receives money in their Stripe Connect         │
└─────────────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│              POST-PAYMENT TASKS                             │
│  ADMIN:                                                     │
│  - Monitor payments & transfers                             │
│  - Handle disputes & chargebacks                           │
│  - Approve refunds                                          │
│  - View reports & analytics                                │
│                                                             │
│  SELLER:                                                    │
│  - View order (thông báo qua notification)                 │
│  - Ship order                                               │
│  - Receive payment (tự động transfer)                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 Seller Role trong Payment

### Seller Là GÌ:
- ✅ **Người bán hàng**
- ✅ **Nhận tiền từ platform**
- ❌ **KHÔNG xử lý payment**
- ❌ **KHÔNG cấu hình Stripe**

### Seller Cần Làm:
1. **Onboarding** (lần đầu)
   ```bash
   # Admin tạo seller account trên Stripe Connect
   # Seller nhận email: "Connect your Stripe account"
   # Seller click link → Authorize → Done
   
   # Hoặc seller tự onboard:
   # Truy cập seller app → Settings → Connect Stripe
   # Redirect to Stripe → Authorize → Get Stripe Account ID
   ```

2. **Management**
   ```bash
   # Seller app → Payments tab
   # - View order status
   # - View payment status: PENDING → PAID
   # - View transfer status: settled
   # - View payment history
   # - Withdraw to bank account (Stripe dashboard)
   ```

3. **No Configuration Needed**
   ```bash
   # Seller KHÔNG cần:
   # ❌ Setup Stripe keys
   # ❌ Configure webhook
   # ❌ Manage payments
   # ❌ Handle refunds (admin does)
   
   # All automatic! ✅
   ```

### Seller Flow Example

```bash
# STEP 1: Register Seller
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "seller_test",
    "email": "seller@shop.local",
    "password": "Password123!",
    "phone": "0901234567",
    "role": "SELLER"
  }'
# ✅ Seller registered

# STEP 2: Seller Onboards Stripe (Optional - Admin can do it)
# Seller goes to: Seller App → Settings → Connect Stripe
# OR Admin creates Stripe Connect account for seller

# STEP 3: Seller Adds Product
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sku_code": "PROD-001",
    "name": "Laptop",
    "price": 10000000,
    "stock": 5
  }'
# ✅ Product added

# STEP 4: Customer buys → Payment processed
# Seller gets notification: "Order #123 paid!"
# Transfer automatically sent to seller's Stripe Connect: 9,500,000 VND (after 5% fee)

# STEP 5: Seller ships product
# Seller goes to Seller App → Orders → Update status
curl -X PUT http://localhost:8080/api/v1/orders/123/status \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "SHIPPED", "tracking_number": "DHL123"}'
# ✅ Status updated

# STEP 6: Seller receives money
# In Stripe dashboard: Payouts section shows transferred amount
```

---

## 👨‍💼 Admin Role trong Payment

### Admin Là GÌ:
- ✅ **Platform owner/operator**
- ✅ **Setup Stripe**
- ✅ **Configure payment system**
- ✅ **Manage seller onboarding**
- ✅ **Handle payments & disputes**

### Admin Cần Làm:

#### 1️⃣ SETUP PHASE (One-time)

```bash
# A. Create Stripe Platform Account
# - Go to: https://dashboard.stripe.com/register
# - Create FREE account
# - Enable "Stripe Connect" (for multiple sellers)

# B. Setup Stripe Connect for sellers
# Option 1: Restrict Accounts (Dashboard controls seller Stripe)
#   - Admin creates Stripe Connect accounts for sellers
#   - Admin manages their settings
#   
# Option 2: Standard Accounts (Sellers manage their own Stripe)
#   - Sellers connect their own Stripe accounts
#   - More autonomy

# C. Get Stripe Keys
# - Developers → API Keys
# - Copy Secret Key: sk_test_xxxxx
# - Copy Webhook Secret: whsec_test_xxxxx (from stripe listen)

# D. Configure Application (.env)
STRIPE_SECRET_KEY=sk_test_xxxxx
STRIPE_WEBHOOK_SECRET=whsec_test_xxxxx
STRIPE_PLATFORM_FEE_PERCENTAGE=5.0

# E. Start Stripe CLI (keep running)
stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks
```

#### 2️⃣ SELLER ONBOARDING

```bash
# Admin API: Onboard Seller to Stripe
curl -X POST http://localhost:8080/api/v1/sellers/123/stripe/onboard \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "seller_id": 123,
    "email": "seller@shop.local",
    "account_type": "standard"
  }'

# Result:
# - Seller gets email: "Connect your Stripe account"
# - Seller clicks → Authorizes → Returns with Stripe Account ID
# - System stores: stripe_connect_account_id

# OR Seller self-onboards:
# Seller app → Settings → Connect Stripe → Gets redirected
```

#### 3️⃣ PAYMENT MONITORING

```bash
# Admin Dashboard
# - View all payments (order_id, amount, status)
# - View all transfers (seller_id, amount, stripe_id, status)
# - View platform balance (total received - total transferred)
# - View disputes & chargebacks

# API Example:
# List all payments
curl -X GET http://localhost:8080/api/v1/admin/payments?page=0&size=20 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
# Response:
# [
#   {
#     "transaction_id": 1,
#     "order_id": 123,
#     "amount": 200000,
#     "status": "COMPLETED",
#     "seller_transfer": {
#       "amount": 190000,
#       "status": "SETTLED"
#     }
#   }
# ]
```

#### 4️⃣ REFUND HANDLING

```bash
# Admin can approve refunds (seller initiates)

# Seller requests refund:
curl -X POST http://localhost:8080/api/v1/refunds \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "order_id": 123,
    "amount": 200000,
    "reason": "Product defective"
  }'

# Admin approves:
curl -X POST http://localhost:8080/api/v1/admin/refunds/1/approve \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tracking_number": "RTS-123-456"
  }'

# Result:
# - Refund processed to Buyer
# - Reversal transfer from Seller (if already paid)
# - Kafka event: refund.completed
```

#### 5️⃣ DISPUTE MANAGEMENT

```bash
# Admin handles chargebacks/disputes from Stripe

# Webhook: charge.dispute.created
# Admin gets notified

# Admin response:
curl -X POST http://localhost:8080/api/v1/admin/disputes/123/respond \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "evidence": "Order fulfilled, tracking proof attached",
    "evidence_file": "url-to-proof"
  }'
```

#### 6️⃣ REPORTING

```bash
# Admin Views Reports

# Payment Summary
curl -X GET http://localhost:8080/api/v1/admin/reports/payment-summary?period=month \
  -H "Authorization: Bearer $ADMIN_TOKEN"
# Response:
# {
#   "total_volume": 50000000,
#   "successful_payments": 250,
#   "failed_payments": 5,
#   "platform_fees": 2500000,
#   "refunds": 500000,
#   "net_revenue": 2000000
# }

# Seller Payouts
curl -X GET http://localhost:8080/api/v1/admin/reports/seller-payouts \
  -H "Authorization: Bearer $ADMIN_TOKEN"
# Response:
# [
#   {
#     "seller_id": 43,
#     "seller_name": "Tech Shop",
#     "total_paid": 47500000,
#     "pending": 0,
#     "next_payout": "2026-04-26"
#   }
# ]
```

#### 7️⃣ SETTINGS MANAGEMENT

```bash
# Admin can change fee percentage
curl -X PUT http://localhost:8080/api/v1/admin/settings/payment \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "platform_fee_percentage": 7.5,
    "min_payout_amount": 100000,
    "payout_schedule": "weekly"
  }'
```

---

## 📋 Comparison: Seller vs Admin

| Task | Seller | Admin |
|------|--------|-------|
| **Setup Stripe** | ❌ | ✅ |
| **Configure Keys** | ❌ | ✅ |
| **Setup Webhook** | ❌ | ✅ |
| **Onboard Sellers** | ❌ | ✅ |
| **Receive Payment** | ✅ (automatic) | - |
| **View Orders** | ✅ | ✅ |
| **Approve Refunds** | ❌ | ✅ |
| **Handle Disputes** | ❌ | ✅ |
| **View Reports** | ✅ (own only) | ✅ (all) |
| **Manage Fee %** | ❌ | ✅ |
| **View Platform Balance** | ❌ | ✅ |

---

## 🔄 Complete Admin Setup Guide

### 5-Minute Setup for Admin

```bash
#!/bin/bash
# admin_payment_setup.sh

echo "=== Admin Payment Setup ==="

# 1. Create Stripe Account (1 min)
echo "1. Create Stripe account at https://dashboard.stripe.com"
echo "   - Enable Stripe Connect"
echo "   - Get API Keys from Developers tab"

# 2. Setup Stripe CLI (1 min)
echo ""
echo "2. Setup Stripe CLI..."
brew install stripe/stripe-cli/stripe  # or choco for Windows
stripe login

# 3. Forward webhooks (1 min)
echo ""
echo "3. Starting Stripe webhook forwarding (keep this running)..."
stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks
# Copy: whsec_test_xxxxx

# 4. Configure .env (1 min)
echo ""
echo "4. Update .env with Stripe keys..."
read -p "Enter Stripe Secret Key: " STRIPE_KEY
read -p "Enter Stripe Webhook Secret: " STRIPE_WEBHOOK

cat >> .env << EOF
STRIPE_SECRET_KEY=$STRIPE_KEY
STRIPE_WEBHOOK_SECRET=$STRIPE_WEBHOOK
STRIPE_PLATFORM_FEE_PERCENTAGE=5.0
EOF

# 5. Restart services (1 min)
echo ""
echo "5. Restarting services..."
docker-compose down
docker-compose up -d
sleep 10

# 6. Verify
echo ""
echo "6. Verifying setup..."
curl -s http://localhost:8082/actuator/health | jq '.status'

echo ""
echo "✅ Admin setup complete!"
```

---

## 🎓 Quick Summary

### ❓ Seller chỉ cần gắn Stripe vào thôi?
**YES! 👍**
- Seller chỉ cần authorize Stripe Connect (1 click)
- Seller không cần setup gì cả
- Tất cả automatic!

### ❓ Admin có làm gì không?
**YES! Admin làm nhiều! 💼**

**Setup (1 time):**
- ✅ Create Stripe platform account
- ✅ Setup Stripe CLI
- ✅ Configure .env
- ✅ Onboard sellers

**Operations (ongoing):**
- ✅ Monitor payments
- ✅ Handle refunds
- ✅ Manage disputes
- ✅ View reports
- ✅ Adjust settings

---

## 💡 Example: Full Flow with Seller + Admin

```bash
# ===== ADMIN SETUP (First time) =====
# 1. Create Stripe account
# 2. Setup CLI: stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks
# 3. Update .env
# 4. Docker restart

# ===== SELLER ONBOARDING =====
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"seller_shop","email":"seller@shop.local","password":"Password123!","phone":"0901234567","role":"SELLER"}'

# Seller gets notification: "Please connect Stripe account"
# Seller app → Settings → Connect Stripe → Click → Authorize

# ===== BUYER PURCHASE =====
# 1. Buyer adds product to cart
# 2. Buyer checkout
# 3. Buyer pays with card: 4242 4242 4242 4242
# 4. Platform receives: 200,000 VND
# 5. Platform deducts fee (5%): 10,000 VND
# 6. Platform transfers to seller: 190,000 VND

# ===== RESULTS =====
# ✅ Order status: PAID
# ✅ Seller gets notification: "Order paid, ready to ship"
# ✅ Admin sees transfer: "190,000 → seller_shop (SETTLED)"
# ✅ Seller's Stripe account: +190,000 VND
```

---

## 🚀 Next Steps for Admin

1. **Immediate**: Setup Stripe account + CLI
2. **Today**: Configure .env + restart services
3. **Tomorrow**: Onboard first seller
4. **This week**: Test payment flow end-to-end
5. **Next week**: Setup monitoring & alerts

---

## Files to Reference

- **Payment Setup**: `docs/PAYMENT_QUICK_GUIDE.md`
- **Full Testing**: `docs/TESTING_GUIDE_VN.md`
- **Business Logic**: `docs/BUSINESS_DOC_v5_3_rts_unified.md`
- **API Spec**: `docs/API_DETAILED_JSON_v5_3_RTS.md`

---

**Summary:**
- 🛍️ **Seller**: Connect Stripe → Receive money (automatic)
- 👨‍💼 **Admin**: Setup Stripe → Monitor → Handle issues → Report

**Simple!** 🎉

