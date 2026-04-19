# ✅ Stripe Setup Complete!

**Ngày**: 2026-04-19  
**Status**: Stripe Keys Added to .env

---

## 🎉 Cập Nhật Hoàn Tất

### Stripe Keys Đã Thêm Vào `.env`:

```bash
✅ STRIPE_SECRET_KEY=sk_test_51T9nfQCma465d2ukx7nMVIiavQiTGv3lB1VzS2mPlNsxMQIfzFpSM9wxmBXRSlYLDmXLsxOen3ZEKHGPkhMCfQnz00JPBBaLVR
✅ STRIPE_PUBLISHABLE_KEY=pk_test_51T9nfQCma465d2ukoCIpI2VFgCVS1KF5FkPOtMdmaqH6ySwKbqgsiWtZteioB7ylWbERYug2ttcfewyjEqSZoHIt00W3x8V6jJ
✅ STRIPE_WEBHOOK_SECRET=whsec_98s40McZSaPXsXT3vfNPe0jhUPyasZDv
✅ STRIPE_PLATFORM_FEE_PERCENTAGE=5.0
```

---

## 🚀 Next Steps

### Bước 1: Restart Docker (5 phút)

```bash
# Vào thư mục project
cd stealing-from-paradise

# Restart services với config mới
docker-compose down
docker-compose up -d

# Chờ 3-5 phút cho services khởi động
docker-compose ps  # Kiểm tra trạng thái
```

### Bước 2: Verify Stripe Config

```bash
# Check payment-service logs
docker-compose logs payment-service | grep -i stripe

# Kết quả nên chứa:
# ✅ "Stripe configured successfully"
# ✅ "Stripe secret key loaded"
# ✅ "Webhook configured"
```

### Bước 3: Setup Stripe CLI (Local Webhook Testing)

```bash
# Terminal mới - Forward webhooks (GIỮ LUN CHẠY!)
stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks

# Nếu lỗi "command not found":
# macOS: brew install stripe/stripe-cli/stripe
# Windows: choco install stripe-cli
# Linux: https://github.com/stripe/stripe-cli/releases
```

### Bước 4: Test Setup

```bash
# Verify payment service health
curl http://localhost:8082/actuator/health
# Kết quả: {"status":"UP"}

# Test Stripe connection
curl https://api.stripe.com/v1/payment_intents \
  -u sk_test_51T9nfQCma465d2ukx7nMVIiavQiTGv3lB1VzS2mPlNsxMQIfzFpSM9wxmBXRSlYLDmXLsxOen3ZEKHGPkhMCfQnz00JPBBaLVR: \
  -d amount=1000 \
  -d currency=usd | jq '.id'

# Kết quả: "pi_test_xxxxx"
```

---

## 🧪 Quick Test: End-to-End Payment

### Option 1: Browser (Easiest)

```bash
# 1. Open http://localhost:3000 (Customer App)
# 2. Register buyer
# 3. Browse & add product to cart
# 4. Checkout
# 5. Payment page
# 6. Enter test card: 4242 4242 4242 4242
#    Exp: 12/26, CVC: 123
# 7. Click Pay
# 8. Wait 3-5 sec
# 9. See "Payment Successful" ✅
```

### Option 2: API (Automated)

```bash
#!/bin/bash
# Save as: test_payment_final.sh
chmod +x test_payment_final.sh
./test_payment_final.sh

#!/bin/bash
# test_payment_final.sh

echo "=== Final Payment Test with Real Stripe Keys ==="

# Create seller
SELLER=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"seller_final_'$(date +%s)'","email":"seller_'$(date +%s)'@test.local","password":"Password123!","phone":"0901234567","role":"SELLER"}')
SELLER_TOKEN=$(echo $SELLER | jq -r '.access_token')
echo "✅ Seller registered"

# Create product
PRODUCT=$(curl -s -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sku_code":"FINAL-'$(date +%s)'","name":"Final Test Product","price":100000,"stock":10,"category_id":1}')
PRODUCT_ID=$(echo $PRODUCT | jq '.id')
echo "✅ Product added: $PRODUCT_ID"

# Create buyer
BUYER=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"buyer_final_'$(date +%s)'","email":"buyer_'$(date +%s)'@test.local","password":"Password123!","phone":"0901234568"}')
BUYER_TOKEN=$(echo $BUYER | jq -r '.access_token')
echo "✅ Buyer registered"

# Add to cart
curl -s -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"product_id":'$PRODUCT_ID',"quantity":1}' > /dev/null
echo "✅ Added to cart"

# Create address
ADDRESS=$(curl -s -X POST http://localhost:8080/api/v1/addresses \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"full_address":"123 Test St","province_id":79,"district_id":760,"phone":"0901234568","is_default":true}')
ADDRESS_ID=$(echo $ADDRESS | jq '.id')
echo "✅ Address created"

# Checkout
echo ""
echo "Creating order..."
ORDER=$(curl -s -X POST http://localhost:8080/api/v1/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"address_id":'$ADDRESS_ID',"item_ids":["item_1"],"use_loyalty_points":false}')

ORDER_ID=$(echo $ORDER | jq '.orders[0].order_id')
PARENT_ORDER_ID=$(echo $ORDER | jq '.parent_order_id')
AMOUNT=$(echo $ORDER | jq '.payment.final_amount')
echo "✅ Order created!"
echo "   Order ID: $ORDER_ID"
echo "   Amount: $AMOUNT VND"

# Get Stripe PaymentIntent
echo ""
echo "Checking Stripe PaymentIntent..."
PAYMENT=$(curl -s -X GET http://localhost:8080/api/v1/payments/parent-order/$PARENT_ORDER_ID \
  -H "Authorization: Bearer $BUYER_TOKEN")
STRIPE_PI=$(echo $PAYMENT | jq -r '.stripe_pi_id')
echo "✅ PaymentIntent: $STRIPE_PI"

# Simulate payment with Stripe CLI
echo ""
echo "Simulating payment..."
echo "⚠️  Make sure 'stripe listen' is running in another terminal!"
stripe trigger payment_intent.succeeded
sleep 5

# Check result
echo ""
echo "Checking result..."
FINAL_STATUS=$(curl -s -X GET http://localhost:8080/api/v1/orders/$ORDER_ID \
  -H "Authorization: Bearer $BUYER_TOKEN" | jq -r '.status')
echo ""
echo "========================================="
echo "FINAL STATUS: $FINAL_STATUS"
if [ "$FINAL_STATUS" = "PAID" ]; then
  echo "✅ SUCCESS! Payment processed!"
else
  echo "⚠️  Status: $FINAL_STATUS (expected PAID)"
fi
echo "========================================="
```

### Option 3: Verify in Stripe Dashboard

```bash
# 1. Go to: https://dashboard.stripe.com/test/payments
# 2. Look for PaymentIntent with status: succeeded
# 3. Click to see charge details
# 4. Verify amount matches
```

---

## ✅ Verification Checklist

- [ ] Docker services restarted
- [ ] Payment service logs show "Stripe configured"
- [ ] Stripe CLI running: `stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks`
- [ ] curl to Stripe API works
- [ ] Payment test script runs successfully
- [ ] Order status changes from PENDING → PAID
- [ ] Stripe Dashboard shows successful payment
- [ ] Database shows transaction recorded
- [ ] Kafka shows payment.success event

---

## 📊 Key Files

| File | Purpose |
|------|---------|
| `.env` | Stripe keys (UPDATED ✅) |
| `TESTING_GUIDE_VN.md` | Full testing guide |
| `PAYMENT_QUICK_GUIDE.md` | Quick reference |
| `PAYMENT_SELLER_VS_ADMIN.md` | Roles explanation |

---

## 🎯 You're Ready!

**Done Setup? Now:**

1. **Restart services** (docker-compose down && up)
2. **Run test payment** (./test_payment_final.sh)
3. **Verify in Stripe** (dashboard)
4. **Check database** (psql)
5. **Check Kafka** (kafka-console-consumer)

---

## ⚠️ Security Notes

```bash
# IMPORTANT: Protect these keys!
# ✅ Never commit .env to GitHub
# ✅ Never share keys in public
# ✅ Rotate keys regularly in production
# ✅ Use environment variables in production

# Check .gitignore
cat .gitignore | grep env
# Should include: .env, .env.local, etc.
```

---

## 🆘 Troubleshooting

### "Connection refused to Stripe"

```bash
# Check internet connection
curl https://api.stripe.com

# Verify keys are correct
grep STRIPE .env

# Check payment-service logs
docker-compose logs payment-service | grep -i error
```

### "Webhook not received"

```bash
# Ensure Stripe CLI is running
# Terminal should show: > Ready! Your webhook signing secret is...

# Restart Stripe CLI
stripe listen --forward-to localhost:8082/api/v1/stripe/webhooks
```

### "PaymentIntent fails"

```bash
# Check config loaded
docker-compose logs payment-service | grep -i "stripe configured"

# Verify webhook secret matches
grep STRIPE_WEBHOOK .env
stripe trigger payment_intent.succeeded  # Check Stripe CLI output
```

---

## 🚀 Production Deployment

When ready for production:

1. **Use live Stripe keys** (not test keys)
2. **Update .env with production keys**
3. **Enable SSL/HTTPS**
4. **Setup monitoring & alerts**
5. **Test thoroughly before going live**

---

**Everything is set! Ready to test? Let's go! 🎉**

