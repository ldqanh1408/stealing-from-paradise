# Manual API Run Report — 2026-06-10 (Docker Compose Dev)

## Environment
- Stack: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d` (started 20:56 ICT)
- All 25 containers up; 8 services `(healthy)` in Docker
- **Host networking note:** Windows Docker Desktop port-proxy returned `Empty Reply from server` for every host→`127.0.0.1:8080` request (TCP connect OK, server closed without HTTP response). Same Windows vpnkit/wsl bug noted in [E2E_PAYMENT_REPORT_2026-06-10.md](E2E_PAYMENT_REPORT_2026-06-10.md). **Workaround:** ran the entire suite from a sidecar container `e2e-runner` (alpine + curl 8.19 + python3 + jq) attached to `flashsale-net`, talking to `http://api-gateway:8080` directly. All UCs below executed this way.

## Stripe Webhook Forge Notes
- `stripe-java` 26.1.0 → `Stripe.API_VERSION = "2024-06-20"`. Forged events must use this api_version or `EventDataObjectDeserializer` rejects the payload.
- Metadata key is **`parent_order_id`** (snake_case), not `parentOrderId`. The original test-plan helper had this wrong — payment-service silently ignored the event.
- Payload must include `object: "event"`, `created`, `livemode`, `pending_webhooks` at envelope level + nested `data.object`.
- Signed forge port → `D:\dev\cc113\stealing-from-paradise\.omc\state\forge.py` (uses `urllib` + `hmac.sha256`).

---

## Results Summary

| UC | Flow | Verdict | Evidence |
|----|------|:-------:|----------|
| 11.1 | Checkout → SUCCESS → PAID | ✅ PASS | parent=170, sub#186 PAID, tx=106 SUCCESS, amount=1,290,000 VND, paidAt=21:25:00 |
| 11.2 | Checkout → FAILED → CANCELLED | ✅ PASS | parent=171, sub#187 CANCELLED, tx=107 FAILED |
| 11.3 | Buyer cancels PENDING | ✅ PASS | parent=172, sub#188 CANCELLED via `/orders/{id}/cancel`, tx=108 CANCELLED |
| 11.4 | Fulfillment + partial refund | ⚠️ PARTIAL | Ship/deliver/refund-record OK (refundId=14, status=PENDING); **admin approve blocked**: Stripe rejects `pi_3TgmwdCma465d2uk0xCBDfkG does not have a successful charge to refund` — forge updates DB only, real Stripe PI never charged |
| 11.5 | Flash sale lifecycle | ✅ PASS | session=30, item=36 APPROVED, reminder set, buy → parent=173 sub#189 totalAmt=99,000 VND, isFlashSale=true |
| 11.6 | Stripe Connect onboarding | ✅ PASS | new seller ucseller1781102561, mock acct=`acct_mock_53_538f87ce`, status COMPLETE, `account.updated` webhook → chargesEnabled=true |
| 11.7 | Payment idempotency | ✅ PASS | Two reads of `/payments/parent-order/170` → both txId=106 |
| 11.8 | Multi-seller cart | ❌ **FAIL — real bug** | Checkout accepted (totalAmount=1,989,000 VND, sellers 1+4); order-service consumer fails to persist with `duplicate key value violates unique constraint "orders_order_code_key"` → no parent order created |
| 11.9 | charge.refunded webhook | ✅ PASS | http=200 body=received for parent=170, amount=50,000 |
| 11.10 | transfer.created / reversed | ⚠️ PARTIAL | Both webhooks accepted (http=200); `/seller/payments/transfers` query returns `success=false` empty — may need fix or extra wiring |
| 11.11 | Webhook signature negative | ⚠️ MIXED | bad-sig → 400 "Invalid Stripe signature" ✅; **unsigned → 500 SYS_001** (should be 400) — minor bug |
| 11.12 | Product publish → search reindex | ❌ FAIL | Product created (`ab7a22f6-…`), variant created. `POST /products/{id}/images` returns "An unexpected error occurred" → submit/approve/publish all blocked |
| 11.13 | Order paid → notification | ❌ FAIL | parent=182 reached SUCCESS, but `unread_count` stayed 0 (before=0, after=0). notification-service shows no kafka consumption logs in window — consumer not picking up `order.paid` / `payment.success` events |

**Score:** 7 PASS · 3 PARTIAL · 3 FAIL out of 13 UCs.

---

## Real Bugs Found

### Bug-1 — Multi-seller checkout order_code collision (UC-11.8)  [Severity: HIGH]
**Service:** order-service · `CheckoutSubmittedConsumer`
**Repro:** Add two variants from different sellers to cart, preview, submit.
**Symptom:** Consumer throws `ConstraintViolationException: duplicate key value violates unique constraint "orders_order_code_key"` — no parent or sub-orders are persisted.
**Log:**
```
2026-06-10T21:46:09.337+07:00 WARN  ERROR: duplicate key value violates unique constraint "orders_order_code_key"
2026-06-10T21:46:09.338+07:00 ERROR c.f.o.c.CheckoutSubmittedConsumer Error processing order.checkout_submitted event
… items: [seller_id=1 SKU-MAGSAFE, seller_id=4 SKU-HUB-7IN1] total_amount=1989000.00 …
```
**Likely cause:** `order_code` generated from a per-checkout timestamp/sequence without per-seller suffix → both sub-orders try to insert `OR-20260610-NNN` with the same NNN.
**Impact:** Multi-seller checkouts cannot create orders at all → blocks core marketplace flow.

### Bug-2 — Unsigned Stripe webhook returns 500 (UC-11.11)  [Severity: LOW]
**Endpoint:** `POST /api/v1/stripe/webhooks`
**Repro:** POST without `Stripe-Signature` header.
**Actual:** `500 SYS_001 Lỗi nội bộ`
**Expected:** `400 VAL_001 Invalid Stripe signature` (consistent with bad-sig path).
**Impact:** Cosmetic — request is correctly rejected; just wrong status code for monitoring/alerting.

### Bug-3 — Product image add silently errors (UC-11.12)  [Severity: MEDIUM]
**Endpoint:** `POST /api/v1/products/{productId}/images`
**Payload tested:** `{"imageUrl":"https://picsum.photos/...","isPrimary":true}`
**Response:** `{"success":false, "message":"An unexpected error occurred"}`
**Impact:** Product publish chain unverifiable from a seller-only flow — manual workaround needed (probably presigned-URL + S3 upload). Suspect strict schema validation or missing field; needs schema docs.

### Bug-4 — Notification consumer silent on order paid (UC-11.13)  [Severity: HIGH]
**Service:** notification-service
**Repro:** Buyer pays an order → reaches SUCCESS → unread_count remains unchanged.
**Observation:** notification-service log shows only JWT auth filter events; no `KafkaListener` consume logs for `order.paid` / `payment.success` topics in the 2-minute window after payment.
**Likely cause:** Either consumer group not subscribing, or topic naming mismatch, or filter rule drops the event. notification-service did NOT generate a notification record for order#186 (UC-11.4) or order#182 (UC-11.13).
**Impact:** Buyer never gets paid-order notification → real UX gap.

---

## Doc Bugs in MANUAL_API_TEST_PLAN.md to Fix

| Where | Issue | Fix |
|---|---|---|
| Section 4 / UC-11.1 | Login response parsed `accessToken` at root | Should be `data.accessToken` |
| Section 4 / UC-11.1 | Sub-orders field name | Schema uses `data.orders[]`, not `data.subOrders[]` |
| Section 7 / UC-11.4 | Admin approve body `{}` | Must include `{"adminNote":"..."}` — endpoint validates field |
| Section 7 / UC-11.4 | Refund creation response doesn't carry `refundId` | Buyer/Admin GET endpoints DO; UC needs to list after create instead of relying on POST body |
| Stripe forge (helper) | metadata key `parentOrderId` | Use snake_case `parent_order_id`; also need `object/api_version/created/livemode/pending_webhooks` envelope fields. Reference forge.py in `.omc/state/` |
| UC-11.5 timestamps | Used UTC | Service parses naive ISO as Vietnam local (UTC+7). Use `tz=timezone(timedelta(hours=7))` |
| Sidecar pattern | Plan assumes host curl | On Windows the port-proxy returns Empty Reply. Add explicit sidecar instructions: `docker run -d --name e2e-runner --network flashsale-net alpine sh -c "apk add curl jq python3; tail -f /dev/null"` |

---

## Artifacts
- Sidecar container: `e2e-runner` (still running, attached to `flashsale-net`)
- Tokens & state: inside container at `/tmp/uc/{minhhoa,techworld,admin}.tok`, `parent.id`, etc.
- Stripe forge: [`forge.py`](../.omc/state/forge.py)
- Stripe.API_VERSION extracted: `2024-06-20` (from `/app/app.jar!/BOOT-INF/lib/stripe-java-26.1.0.jar!/com/stripe/Stripe.class`)
- DB queries used `psql -U postgres -d flashsale_platform` (password `postgres123!`), schemas: `payment`, `refund`, etc.

## Recommended Next Steps
1. **Bug-1 (multi-seller order_code)** — read `CheckoutSubmittedConsumer` order_code generator and fix sequence/sub-order disambiguation.
2. **Bug-4 (notification silent)** — verify notification-service kafka topic config matches the producers in order/payment.
3. **Bug-3 (image add)** — log/inspect actual error; document required schema.
4. **Bug-2 (unsigned webhook 500)** — add NPE/missing-header guard before signature verifier.
5. Update [MANUAL_API_TEST_PLAN.md](MANUAL_API_TEST_PLAN.md) per the Doc Bugs table above.
6. Document Windows host networking sidecar workaround prominently in Pre-flight section.
