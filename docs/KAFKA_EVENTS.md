# 🧭 Kafka Events & Topics Catalog

**Project**: stealing-from-paradise Marketplace  
**Version**: v5.3 RTS  
**Last Updated**: 2026-05-01

Tổng hợp đầy đủ tất cả Kafka topics, events, producers, consumers và data payloads.

---

## 📊 Overview

| Metric | Value |
|--------|-------|
| **Total Topics** | 41 (29 event topics + 12 request-reply topics) |
| **Event Producers** | 8 services |
| **Event Consumers** | 8 services |
| **Max Consumers per Topic** | 4+ (Notification Service) |
| **Retention Policy** | 7 days (event topics) |
| **Request-Reply Topics** | 12 topics (6 pairs) — xem [11_KAFKA_REQUEST_REPLY.md](11_KAFKA_REQUEST_REPLY.md) |

---

## 🔐 Identity Service Topics

### 1. account.locked

**Producer**: Identity Service  
**Consumers**: Notification Service, Search Service  
**Event Type**: Account Status Change

**Trigger**: 
- Admin locks user account via `/admin/users/{userId}/lock`
- Automatic lock when trust score < 10 (JOB-X)

**Payload**:
```json
{
  "event_id": "evt_20251001_001",
  "event_type": "account.locked",
  "timestamp": "2025-10-01T14:30:00Z",
  "user_id": 42,
  "username": "nguyen_van_a",
  "lock_reason": "Trust score too low",
  "locked_until": "2026-05-15T10:00:00Z",
  "locked_until_type": "temporary|permanent",
  "old_status": "ACTIVE",
  "new_status": "LOCKED",
  "locked_by": "SYSTEM|admin_id"
}
```

**Consumer Actions**:
- **Notification Service**: Send lock notification to user
- **Search Service**: Hide seller's products from storefront
- **Identity Service**: Revoke all active JWTs for this user

---

### 2. account.auto_locked

**Producer**: Worker Service (JOB-X)  
**Consumers**: Notification Service

**Event Type**: Auto-generated Account Lock

**Trigger**: Scheduled job detects trust score violation

**Payload**:
```json
{
  "event_id": "evt_20251001_002",
  "event_type": "account.auto_locked",
  "timestamp": "2025-10-01T03:00:00Z",
  "user_id": 42,
  "username": "nguyen_van_a",
  "trigger_reason": "BUYER_CANCEL_EXCESSIVE|LOW_TRUST_SCORE",
  "trigger_metric": 5,
  "threshold": 3,
  "trust_score_before": 15,
  "trust_score_after": 10,
  "lock_duration_days": 30
}
```

**Consumer Actions**:
- **Notification Service**: Send urgent lock notification

---

### 3. account.unlocked

**Producer**: Identity Service  
**Consumers**: Notification Service

**Event Type**: Account Unlock

**Trigger**: Admin unlocks account via `/admin/users/{userId}/unlock`

**Payload**:
```json
{
  "event_id": "evt_20251001_003",
  "event_type": "account.unlocked",
  "timestamp": "2025-10-01T15:30:00Z",
  "user_id": 42,
  "username": "nguyen_van_a",
  "unlocked_by": "admin_id",
  "admin_note": "Appeal approved - trust restored",
  "old_status": "LOCKED",
  "new_status": "ACTIVE"
}
```

**Consumer Actions**:
- **Notification Service**: Send unlock confirmation to user

---

### 4. appeal.resolved

**Producer**: Identity Service  
**Consumers**: Notification Service

**Event Type**: Trust Score Appeal Decision

**Trigger**: Admin resolves appeal via `/admin/appeals/{appealId}/resolve`

**Payload**:
```json
{
  "event_id": "evt_20251001_004",
  "event_type": "appeal.resolved",
  "timestamp": "2025-10-01T16:00:00Z",
  "appeal_id": 123,
  "user_id": 42,
  "username": "nguyen_van_a",
  "action": "APPROVED|REJECTED",
  "admin_note": "Evidence provided proves system error",
  "result_detail": "Trust score restored by +10 points",
  "trust_score_delta": 10,
  "trust_score_after": 85,
  "evidence_count": 3
}
```

**Consumer Actions**:
- **Notification Service**: Send decision notification with details

---

### 5. loyalty.points_earned

**Producer**: Identity Service (consolidated Loyalty Service)  
**Consumers**: Notification Service

**Event Type**: Loyalty Points Credit

**Trigger**: Order delivered, buyer confirms receipt

**Payload**:
```json
{
  "event_id": "evt_20251001_005",
  "event_type": "loyalty.points_earned",
  "timestamp": "2025-10-01T17:00:00Z",
  "user_id": 42,
  "username": "nguyen_van_a",
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "order_amount": 700000,
  "points_earned": 350,
  "points_status": "CONFIRMED",
  "trust_tier": "PLATINUM",
  "earning_rate": "5%",
  "total_available": 1250,
  "expiry_date": "2026-10-01"
}
```

**Consumer Actions**:
- **Notification Service**: Send points credit notification

---

## 📦 Product Service Topics

### 6. product.created

**Producer**: Product Service  
**Consumers**: Search Service

**Event Type**: New Product Created

**Trigger**: Seller creates product via `POST /products`

**Payload**:
```json
{
  "event_id": "evt_20251001_006",
  "event_type": "product.created",
  "timestamp": "2025-10-01T10:00:00Z",
  "product_id": 101,
  "seller_id": 5,
  "seller_name": "Shop Nike VN",
  "name": "Áo Thun Nike Air",
  "description": "...",
  "category_id": "cat_123",
  "status": "DRAFT",
  "price_range": {
    "min": 300000,
    "max": 450000
  },
  "variant_count": 5,
  "images": ["url1", "url2"]
}
```

**Consumer Actions**:
- **Search Service**: Prepare indexing (wait for APPROVED status)

---

### 7. product.updated

**Producer**: Product Service  
**Consumers**: Search Service

**Event Type**: Product Information Updated

**Trigger**: Seller updates product via `PUT /products/{productId}`

**Payload**:
```json
{
  "event_id": "evt_20251001_007",
  "event_type": "product.updated",
  "timestamp": "2025-10-01T11:00:00Z",
  "product_id": 101,
  "seller_id": 5,
  "changes": {
    "name": "Updated name",
    "description": "Updated description",
    "images": ["new_url1", "new_url2"]
  },
  "status": "APPROVED"
}
```

**Consumer Actions**:
- **Search Service**: Update Elasticsearch index

---

### 8. product.deleted

**Producer**: Product Service  
**Consumers**: Search Service

**Event Type**: Product Deleted (Soft Delete)

**Trigger**: Seller deletes product via `DELETE /products/{productId}`

**Payload**:
```json
{
  "event_id": "evt_20251001_008",
  "event_type": "product.deleted",
  "timestamp": "2025-10-01T12:00:00Z",
  "product_id": 101,
  "seller_id": 5,
  "deleted_at": "2025-10-01T12:00:00Z",
  "hard_delete_at": "2025-12-30T00:00:00Z",
  "retention_reason": "stock_locked > 0, waiting for pending orders to complete",
  "stock_locked": 5
}
```

**Consumer Actions**:
- **Search Service**: Remove from Elasticsearch index
- **Cart Service**: May trigger items removal if not purchased

---

### 9. category.updated

**Producer**: Product Service  
**Consumers**: Search Service

**Event Type**: Product Category Updated

**Trigger**: Admin updates category via `PUT /admin/categories/{categoryId}`

**Payload**:
```json
{
  "event_id": "evt_20251001_009",
  "event_type": "category.updated",
  "timestamp": "2025-10-01T13:00:00Z",
  "category_id": "cat_123",
  "name": "Category Name",
  "slug": "category-slug",
  "parent_id": "cat_456",
  "level": 2,
  "changes": ["name", "slug"],
  "affected_product_count": 145
}
```

**Consumer Actions**:
- **Search Service**: Update category filters and facets

---

### 10. inventory.adjusted

**Producer**: Product Service  
**Consumers**: Search Service

**Event Type**: Inventory Stock Adjusted

**Trigger**: Seller adjusts stock via `POST /seller/inventory/adjust`

**Payload**:
```json
{
  "event_id": "evt_20251001_010",
  "event_type": "inventory.adjusted",
  "timestamp": "2025-10-01T14:00:00Z",
  "sku_code": "NK-AIR-RED-XL",
  "product_id": 101,
  "seller_id": 5,
  "delta": 20,
  "reason": "RESTOCK|CORRECTION|DAMAGE",
  "stock_before": 80,
  "stock_after": 100,
  "stock_locked": 5,
  "stock_available": 95
}
```

**Consumer Actions**:
- **Search Service**: Update product stock status in index

---

## 📋 Order Service Topics

### 11. order.created

**Producer**: Order Service  
**Consumers**: Inventory Service, Search Service

**Event Type**: Order Checkout Initiated

**Trigger**: Buyer checks out via `POST /orders/checkout`

**Payload**:
```json
{
  "event_id": "evt_20251001_011",
  "event_type": "order.created",
  "timestamp": "2025-10-01T10:00:00Z",
  "parent_order_id": 55,
  "order_code": "PO-20251001-55",
  "buyer_id": 42,
  "orders": [
    {
      "order_id": 100,
      "order_code": "OR-20251001-100",
      "seller_id": 5,
      "total_amount": 700000,
      "items": [
        {
          "order_item_id": 501,
          "sku_code": "NK-AIR-RED-XL",
          "quantity": 2,
          "price": 350000
        }
      ]
    }
  ],
  "total_amount": 1200000,
  "items_count": 3,
  "status": "PENDING",
  "timeout_at": "2025-10-01T10:30:00Z"
}
```

**Consumer Actions**:
- **Inventory Service**: Lock stock for each SKU
- **Search Service**: Update sold count (optional)

---

### 12. order.cancelled

**Producer**: Order Service  
**Consumers**: Cart Service, Loyalty Service, Notification Service

**Event Type**: Order Cancelled

**Trigger**: 
- Buyer cancels via `POST /orders/{orderId}/cancel`
- Seller cancels (if they support it)
- JOB-13 auto-cancel stale orders

**Payload**:
```json
{
  "event_id": "evt_20251001_012",
  "event_type": "order.cancelled",
  "timestamp": "2025-10-01T11:00:00Z",
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "parent_order_id": 55,
  "buyer_id": 42,
  "seller_id": 5,
  "total_amount": 700000,
  "cancelled_by": "BUYER|SELLER|SYSTEM",
  "cancel_reason": "Changed mind|Out of stock|Seller cancelled",
  "items": [
    {
      "sku_code": "NK-AIR-RED-XL",
      "quantity": 2,
      "unlock_quantity": 2
    }
  ],
  "loyalty_points_to_refund": 50,
  "trust_score_impact": {
    "event_code": "BUYER_CANCEL_EXCESSIVE",
    "delta": -5,
    "cumulative_cancels_30d": 6
  }
}
```

**Consumer Actions**:
- **Cart Service**: Remove items from cart
- **Loyalty Service**: Refund pending points
- **Inventory Service**: Unlock stock
- **Notification Service**: Send cancellation notification

---

### 13. order.shipped

**Producer**: Order Service  
**Consumers**: Notification Service

**Event Type**: Order Shipped

**Trigger**: Seller updates tracking via `PUT /orders/{orderId}/tracking`

**Payload**:
```json
{
  "event_id": "evt_20251001_013",
  "event_type": "order.shipped",
  "timestamp": "2025-10-01T12:00:00Z",
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "buyer_id": 42,
  "seller_id": 5,
  "tracking_number": "VT123456789",
  "carrier": "ViettelPost",
  "shipping_deadline": "2025-10-04T12:00:00Z",
  "status": "SHIPPING",
  "items_count": 2
}
```

**Consumer Actions**:
- **Notification Service**: Send shipping notification with tracking

---

### 14. order.delivered

**Producer**: Order Service  
**Consumers**: Identity Service, Loyalty Service, Notification Service

**Event Type**: Order Delivered & Confirmed

**Trigger**: Buyer confirms receipt via `POST /orders/{orderId}/confirm-received`

**Payload**:
```json
{
  "event_id": "evt_20251001_014",
  "event_type": "order.delivered",
  "timestamp": "2025-10-01T13:00:00Z",
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "parent_order_id": 55,
  "buyer_id": 42,
  "seller_id": 5,
  "total_amount": 700000,
  "status": "DELIVERED",
  "confirmed_at": "2025-10-01T13:00:00Z",
  "delivery_days": 3,
  "loyalty_points": {
    "pending": 350,
    "confirmed": 350
  },
  "seller_trust_score_delta": 5
}
```

**Consumer Actions**:
- **Identity Service**: Award seller trust score +5
- **Loyalty Service**: Move points from PENDING → CONFIRMED, produce loyalty.points_earned
- **Notification Service**: Send delivery confirmation

---

### 15. order.returned

**Producer**: Order Service  
**Consumers**: Refund Service, Inventory Service, Notification Service

**Event Type**: Return To Sender (RTS) - Goods Returned

**Trigger**: Seller confirms return via `POST /orders/{orderId}/return-to-sender`

**Payload**:
```json
{
  "event_id": "evt_20251001_015",
  "event_type": "order.returned",
  "timestamp": "2025-10-01T14:00:00Z",
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "parent_order_id": 55,
  "buyer_id": 42,
  "seller_id": 5,
  "status": "RETURNED",
  "return_reason": "DELIVERY_FAILED|NO_ANSWER|WRONG_ADDRESS",
  "return_tracking_number": "VT999888777",
  "evidence_count": 2,
  "refund_id": 99,
  "refund_amount": 700000,
  "items": [
    {
      "sku_code": "NK-AIR-RED-XL",
      "quantity": 2,
      "refund_amount": 700000
    }
  ]
}
```

**Consumer Actions**:
- **Refund Service**: Auto-create full refund, process Stripe refund
- **Inventory Service**: Restore stock
- **Notification Service**: Notify buyer of refund initiation

---

### 16. order.checkout_completed

**Producer**: Order Service  
**Consumers**: Cart Service

**Event Type**: Checkout Completed

**Trigger**: Order successfully created and payment initiated

**Payload**:
```json
{
  "event_id": "evt_20251001_016",
  "event_type": "order.checkout_completed",
  "timestamp": "2025-10-01T10:05:00Z",
  "user_id": 42,
  "parent_order_id": 55,
  "cart_item_ids": [201, 202, 203],
  "orders": [100, 101]
}
```

**Consumer Actions**:
- **Cart Service**: Remove checked-out items from user's cart

---

## 💳 Payment Service Topics

### 17. payment.success

**Producer**: Payment Service (Stripe webhook)  
**Consumers**: Order Service, Notification Service

**Event Type**: Payment Successful

**Trigger**: Stripe webhook `payment_intent.succeeded`

**Payload**:
```json
{
  "event_id": "evt_20251001_017",
  "event_type": "payment.success",
  "timestamp": "2025-10-01T10:05:00Z",
  "transaction_id": 301,
  "parent_order_id": 55,
  "buyer_id": 42,
  "amount": 1200000,
  "currency": "VND",
  "method": "STRIPE",
  "stripe_pi_id": "pi_3PxABC",
  "application_fee": 66500,
  "status": "SUCCESS",
  "paid_at": "2025-10-01T10:05:00Z",
  "orders": [100, 101]
}
```

**Consumer Actions**:
- **Order Service**: Mark orders as PAID
- **Notification Service**: Send payment confirmation

---

### 18. payment.failed

**Producer**: Payment Service  
**Consumers**: Order Service, Notification Service

**Event Type**: Payment Failed

**Trigger**: Stripe webhook `payment_intent.payment_failed`

**Payload**:
```json
{
  "event_id": "evt_20251001_018",
  "event_type": "payment.failed",
  "timestamp": "2025-10-01T10:06:00Z",
  "transaction_id": 301,
  "parent_order_id": 55,
  "buyer_id": 42,
  "amount": 1200000,
  "failure_reason": "card_declined|insufficient_funds|expired_card",
  "failure_message": "Your card was declined",
  "stripe_error_code": "card_declined",
  "retry_count": 1,
  "can_retry": true,
  "next_retry_at": "2025-10-01T10:07:00Z"
}
```

**Consumer Actions**:
- **Order Service**: Keep orders as PENDING, unlock stock if retry fails
- **Notification Service**: Send payment failure notification with retry option

---

### 19. refund.requested

**Producer**: Payment Service (Refund Service)  
**Consumers**: Notification Service

**Event Type**: Refund Requested

**Trigger**: Buyer requests refund via `POST /orders/{orderId}/refunds`

**Payload**:
```json
{
  "event_id": "evt_20251001_019",
  "event_type": "refund.requested",
  "timestamp": "2025-10-01T15:00:00Z",
  "refund_id": 88,
  "order_id": 100,
  "parent_order_id": 55,
  "buyer_id": 42,
  "seller_id": 5,
  "amount": 350000,
  "type": "PARTIAL|FULL",
  "reason": "Damaged|Wrong item|Out of stock",
  "items": [
    {
      "order_item_id": 501,
      "quantity": 1,
      "refund_amount": 175000
    }
  ],
  "status": "PENDING",
  "evidence_count": 2
}
```

**Consumer Actions**:
- **Notification Service**: Notify seller of refund request

---

### 20. refund.admin_approved

**Producer**: Payment Service  
**Consumers**: Loyalty Service, Notification Service

**Event Type**: Refund Approved by Admin

**Trigger**: Admin approves refund via `POST /admin/refunds/{refundId}/approve`

**Payload**:
```json
{
  "event_id": "evt_20251001_020",
  "event_type": "refund.admin_approved",
  "timestamp": "2025-10-05T14:00:00Z",
  "refund_id": 88,
  "order_id": 100,
  "buyer_id": 42,
  "seller_id": 5,
  "amount": 350000,
  "adjust_amount": null,
  "stripe_refund_id": "re_3PxABC",
  "admin_id": 1,
  "admin_note": "Evidence verified",
  "caused_by": "SELLER|BUYER",
  "tracking_number": "VC123456789",
  "loyalty_points_to_return": 50,
  "seller_trust_score_delta": -5,
  "status": "SUCCESS"
}
```

**Consumer Actions**:
- **Loyalty Service**: Return loyalty points, update transactions
- **Identity Service**: Adjust seller trust score if caused_by=SELLER
- **Notification Service**: Notify buyer and seller of approval

---

### 21. refund.rejected

**Producer**: Payment Service  
**Consumers**: Notification Service

**Event Type**: Refund Rejected

**Trigger**: Admin rejects refund via `POST /admin/refunds/{refundId}/reject`

**Payload**:
```json
{
  "event_id": "evt_20251001_021",
  "event_type": "refund.rejected",
  "timestamp": "2025-10-06T10:00:00Z",
  "refund_id": 88,
  "order_id": 100,
  "buyer_id": 42,
  "amount": 350000,
  "reject_reason": "No valid evidence provided",
  "fraud_evidence": false,
  "admin_id": 1,
  "buyer_trust_score_delta": 0
}
```

**Consumer Actions**:
- **Notification Service**: Notify buyer of rejection with reason

---

### 22. refund.stripe_auto

**Producer**: Payment Service (Chargeback)  
**Consumers**: Order Service, Loyalty Service

**Event Type**: Stripe Auto-Refund (Chargeback/Dispute)

**Trigger**: Stripe webhook `charge.refunded` (chargeback)

**Payload**:
```json
{
  "event_id": "evt_20251001_022",
  "event_type": "refund.stripe_auto",
  "timestamp": "2025-10-10T08:00:00Z",
  "transaction_id": 301,
  "parent_order_id": 55,
  "buyer_id": 42,
  "amount": 1200000,
  "reason": "CHARGEBACK|DISPUTE|CARD_RETRIEVAL",
  "stripe_dispute_id": "dp_3PxDEF",
  "reason_detail": "Customer reports unauthorized transaction",
  "auto_refund": true
}
```

**Consumer Actions**:
- **Order Service**: Mark orders as refunded
- **Loyalty Service**: Deduct loyalty points (potential abuse)

---

## ⚡ Flash Sale Service Topics

### 23. flash_sale.session_started

**Producer**: Flash Sale Service (JOB-01)  
**Consumers**: Notification Service

**Event Type**: Flash Sale Session Started

**Trigger**: Session time reaches start_time (JOB-01 every minute)

**Payload**:
```json
{
  "event_id": "evt_20251001_023",
  "event_type": "flash_sale.session_started",
  "timestamp": "2025-11-01T20:00:00Z",
  "session_id": 3,
  "name": "Flash Sale 20h Thứ 6",
  "start_time": "2025-11-01T20:00:00Z",
  "end_time": "2025-11-01T22:00:00Z",
  "item_count": 15,
  "reminder_subscribers": 2450
}
```

**Consumer Actions**:
- **Notification Service**: Send reminders to subscribed users via SSE

---

### 24. flash_sale.session_ended

**Producer**: Flash Sale Service (JOB-01)  
**Consumers**: Notification Service

**Event Type**: Flash Sale Session Ended

**Trigger**: Session time reaches end_time

**Payload**:
```json
{
  "event_id": "evt_20251001_024",
  "event_type": "flash_sale.session_ended",
  "timestamp": "2025-11-01T22:00:00Z",
  "session_id": 3,
  "name": "Flash Sale 20h Thứ 6",
  "total_items_sold": 1245,
  "total_revenue": 125000000,
  "avg_discount": "25%"
}
```

**Consumer Actions**:
- **Notification Service**: Send session end notification
- **Cart Service**: Remove expired flash sale items (JOB-07)

---

### 25. flash_sale.item_approved

**Producer**: Flash Sale Service  
**Consumers**: Notification Service

**Event Type**: Flash Sale Item Approved

**Trigger**: Admin approves item via `POST /flash-sale/sessions/{sessionId}/items/{itemId}/approve`

**Payload**:
```json
{
  "event_id": "evt_20251001_025",
  "event_type": "flash_sale.item_approved",
  "timestamp": "2025-10-31T15:00:00Z",
  "session_id": 3,
  "fs_item_id": 42,
  "seller_id": 5,
  "sku_code": "NK-AIR-RED-XL",
  "flash_price": 250000,
  "original_price": 350000,
  "discount_percent": 29,
  "flash_stock": 100,
  "limit_per_user": 3,
  "admin_note": "Approved"
}
```

**Consumer Actions**:
- **Notification Service**: Notify seller of approval

---

### 26. flash_sale.item_rejected

**Producer**: Flash Sale Service  
**Consumers**: Notification Service

**Event Type**: Flash Sale Item Rejected

**Trigger**: Admin rejects item via `POST /admin/flash-sale/items/{itemId}/reject`

**Payload**:
```json
{
  "event_id": "evt_20251001_026",
  "event_type": "flash_sale.item_rejected",
  "timestamp": "2025-10-31T16:00:00Z",
  "session_id": 3,
  "fs_item_id": 42,
  "seller_id": 5,
  "sku_code": "NK-AIR-RED-XL",
  "reject_reason": "Price too high compared to original"
}
```

**Consumer Actions**:
- **Notification Service**: Notify seller of rejection with reason

---

### 27. flash_sale.item_sold

**Producer**: Flash Sale Service  
**Consumers**: Inventory Service

**Event Type**: Flash Sale Item Sold

**Trigger**: User purchases via `POST /flash-sale/sessions/{sessionId}/buy`

**Payload**:
```json
{
  "event_id": "evt_20251001_027",
  "event_type": "flash_sale.item_sold",
  "timestamp": "2025-11-01T20:15:00Z",
  "session_id": 3,
  "fs_item_id": 42,
  "buyer_id": 42,
  "seller_id": 5,
  "sku_code": "NK-AIR-RED-XL",
  "quantity": 2,
  "flash_price": 250000,
  "total_amount": 500000,
  "sold_qty": 45,
  "remaining_stock": 55,
  "limit_per_user": 3,
  "user_qty_purchased": 2
}
```

**Consumer Actions**:
- **Inventory Service**: Update sold count and remaining stock cache

---

### 28. flash_sale.reminder

**Producer**: Worker Service (JOB-02)  
**Consumers**: Notification Service

**Event Type**: Flash Sale Reminder

**Trigger**: JOB-02 sends reminders 1 hour before session

**Payload**:
```json
{
  "event_id": "evt_20251001_028",
  "event_type": "flash_sale.reminder",
  "timestamp": "2025-11-01T19:00:00Z",
  "session_id": 3,
  "name": "Flash Sale 20h Thứ 6",
  "start_time": "2025-11-01T20:00:00Z",
  "reminder_type": "1_hour_before",
  "subscriber_ids": [42, 50, 75, ...],
  "subscriber_count": 2450
}
```

**Consumer Actions**:
- **Notification Service**: Send SSE reminder to subscribed users

---

## 🔔 Notification Service Topics (Consumer Only)

Notification Service consumes from many topics and produces no events itself. It aggregates and sends notifications.

**Topics Consumed**:
1. account.locked
2. account.auto_locked
3. account.unlocked
4. appeal.resolved
5. loyalty.points_earned
6. product.rejected
7. order.shipped
8. order.returned
9. order.auto_cancelled
10. refund.requested
11. refund.admin_approved
12. refund.rejected
13. flash_sale.session_started
14. flash_sale.session_ended
15. flash_sale.item_approved
16. flash_sale.item_rejected
17. flash_sale.reminder
18. seller.posting_suspended
19. seller.posting_resumed
20. product.auto_hidden

---

## 🛡️ Admin & Worker Service Topics

### 29. account.auto_locked

**Producer**: identity-service (JOB-17 nightly)
**Consumers**: Notification Service

**Trigger**: JOB-17 auto-locks accounts when trust score < 10

**Payload**:
```json
{
  "event_id": "evt_20251001_002",
  "event_type": "account.auto_locked",
  "timestamp": "2025-10-01T03:00:00Z",
  "user_id": 42,
  "username": "nguyen_van_a",
  "trigger_reason": "LOW_TRUST_SCORE",
  "trust_score_before": 8,
  "trust_score_after": 5,
  "lock_duration_days": 30
}
```

**Consumer Actions**:
- **Notification Service**: Send urgent lock notification

---

### 30. seller.posting_suspended

**Producer**: identity-service
**Consumers**: Notification Service

**Trigger**: Admin suspends seller posting via `/admin/users/{userId}/suspend-posting`

**Payload**:
```json
{
  "event_type": "seller.posting_suspended",
  "user_id": 5,
  "seller_id": 5,
  "reason": "Multiple policy violations",
  "suspended_until": "2025-12-01T00:00:00Z"
}
```

---

### 31. seller.posting_resumed

**Producer**: identity-service
**Consumers**: Notification Service

**Trigger**: Admin resumes seller posting

**Payload**:
```json
{
  "event_type": "seller.posting_resumed",
  "user_id": 5,
  "seller_id": 5,
  "note": "Seller completed remediation"
}
```

---

### 32. product.approved

**Producer**: identity-service (via admin action)
**Consumers**: Search Service, Notification Service

**Trigger**: Admin approves product via `/admin/products/{productId}/approve`

**Payload**:
```json
{
  "event_id": "evt_20251001_031",
  "event_type": "product.approved",
  "timestamp": "2025-10-01T15:00:00Z",
  "product_id": 101,
  "seller_id": 5,
  "status": "APPROVED",
  "admin_id": 1,
  "admin_note": "Approved - meets quality standards"
}
```

**Consumer Actions**:
- **Search Service**: Index product in Elasticsearch
- **Notification Service**: Notify seller

---

### 33. product.rejected

**Producer**: identity-service (via admin action)
**Consumers**: Search Service, Notification Service

**Trigger**: Admin rejects product via `/admin/products/{productId}/reject`

**Payload**:
```json
{
  "event_id": "evt_20251001_032",
  "event_type": "product.rejected",
  "timestamp": "2025-10-01T15:30:00Z",
  "product_id": 101,
  "seller_id": 5,
  "status": "REJECTED",
  "reject_reason": "Misleading product images",
  "admin_note": "Images must match actual product"
}
```

**Consumer Actions**:
- **Search Service**: Remove from index
- **Notification Service**: Notify seller with rejection reason

---

### 34. product.auto_hidden

**Producer**: identity-service (JOB-16)
**Consumers**: Search Service, Notification Service

**Trigger**: JOB-16 hides rejected products after 90-day retention period

**Payload**:
```json
{
  "event_id": "evt_20251001_033",
  "event_type": "product.auto_hidden",
  "timestamp": "2025-10-05T00:00:00Z",
  "product_id": 101,
  "seller_id": 5,
  "reason": "Auto-hidden - rejected 90 days ago"
}
```

---

### 35. order.auto_cancelled

**Producer**: order-service (Axon Deadline or JOB-13 safety net)
**Consumers**: Cart Service, Loyalty Service, Notification Service

**Trigger**: Payment timeout exceeded (Axon Deadline fires, JOB-13 as safety net)

**Payload**:
```json
{
  "event_id": "evt_20251001_034",
  "event_type": "order.auto_cancelled",
  "timestamp": "2025-10-01T10:30:00Z",
  "parent_order_id": 55,
  "orders": [100, 101],
  "cancelled_by": "SYSTEM",
  "cancel_reason": "Payment timeout",
  "timeout_minutes": 30
}
```

**Producer**: Identity Service  
**Consumers**: Notification Service

**Trigger**: Admin resumes seller posting

**Payload**:
```json
{
  "event_type": "seller.posting_resumed",
  "user_id": 5,
  "seller_id": 5,
  "note": "Seller completed remediation"
}
```

---

### 31. product.approved

**Producer**: Admin Service  
**Consumers**: Search Service, Notification Service

**Trigger**: Admin approves product via `/admin/products/{productId}/approve`

**Payload**:
```json
{
  "event_id": "evt_20251001_031",
  "event_type": "product.approved",
  "timestamp": "2025-10-01T15:00:00Z",
  "product_id": 101,
  "seller_id": 5,
  "status": "APPROVED",
  "admin_id": 1,
  "admin_note": "Approved - meets quality standards"
}
```

**Consumer Actions**:
- **Search Service**: Index product in Elasticsearch
- **Notification Service**: Notify seller

---

### 32. product.rejected

**Producer**: Admin Service  
**Consumers**: Search Service, Notification Service

**Trigger**: Admin rejects product via `/admin/products/{productId}/reject`

**Payload**:
```json
{
  "event_id": "evt_20251001_032",
  "event_type": "product.rejected",
  "timestamp": "2025-10-01T15:30:00Z",
  "product_id": 101,
  "seller_id": 5,
  "status": "REJECTED",
  "reject_reason": "Misleading product images",
  "admin_note": "Images must match actual product"
}
```

**Consumer Actions**:
- **Search Service**: Remove from index
- **Notification Service**: Notify seller with rejection reason

---

### 33. product.auto_hidden

**Producer**: Worker Service (JOB-16)  
**Consumers**: Search Service, Notification Service

**Trigger**: JOB-16 hides rejected products after retention period

**Payload**:
```json
{
  "event_id": "evt_20251001_033",
  "event_type": "product.auto_hidden",
  "timestamp": "2025-10-05T00:00:00Z",
  "product_id": 101,
  "seller_id": 5,
  "reason": "Auto-hidden - rejected 90 days ago"
}
```

---

### 34. order.auto_cancelled

**Producer**: Worker Service (JOB-13)  
**Consumers**: Cart Service, Loyalty Service, Notification Service

**Trigger**: JOB-13 auto-cancels unpaid orders after timeout

**Payload**:
```json
{
  "event_id": "evt_20251001_034",
  "event_type": "order.auto_cancelled",
  "timestamp": "2025-10-01T10:30:00Z",
  "parent_order_id": 55,
  "orders": [100, 101],
  "reason": "PAYMENT_TIMEOUT",
  "timeout_minutes": 30
}
```

---

## ➕ Topics Bổ Sung (từ `KafkaTopics.java` — chưa có trong catalog trên)

### Review Events (Producer: Product Service)

| Topic | Consumer | Trigger |
|-------|----------|---------|
| `review.created` | Notification (báo seller), Search (update rating) | Buyer submit review |
| `review.updated` | Search (re-index rating) | Buyer edit review |
| `review.deleted` | Search (re-index) | Buyer/Admin xóa review |
| `review.summary_updated` | Search (update `reviewCount`, `avgRating` trong ES index) | Sau mỗi review CRUD |

### Stripe / Payment Extended Events (Producer: Payment Service)

| Topic | Consumer | Trigger |
|-------|----------|---------|
| `stripe.account_suspended` | Notification, Identity | Stripe suspend seller account |
| `stripe.dispute.created` | Notification, Admin log | Buyer open dispute trên Stripe |
| `stripe.dispute.closed` | Notification | Dispute resolved |
| `stripe.transfer.reversed` | Order, Notification | Stripe reverse transfer về platform |
| `stripe.payout.failed` | Notification | Seller payout thất bại |
| `seller.stripe_requirement` | Notification | Stripe yêu cầu seller cung cấp thêm thông tin (KYC) |
| `payment.requested` | Payment Service (internal) | Order tạo payment intent request |

### Refund Extended Events

| Topic | Consumer | Trigger |
|-------|----------|---------|
| `refund.full_requested` | Notification, Payment | Buyer request full refund (RTS flow) |
| `refund.created` | Notification | Refund record đã được tạo (chờ xử lý) |
| `refund.rts_completed` | Order, Notification | Return To Sender hoàn tất |

### Seller Order Events

| Topic | Consumer | Trigger |
|-------|----------|---------|
| `seller.order_cancelled` | Identity (điều chỉnh trust score seller) | Seller hủy đơn chủ động |

---

## 🔄 Request-Reply Topics (12 topics — 6 pairs)

> Các topic này dùng pattern **Kafka Request-Reply** (synchronous-like communication).  
> Xem tài liệu đầy đủ: **[11_KAFKA_REQUEST_REPLY.md](11_KAFKA_REQUEST_REPLY.md)**

| Request Topic | Response Topic | Requester → Responder |
|--------------|----------------|----------------------|
| `cart.product_info.request` | `cart.product_info.response` | Cart → Product catalog |
| `order.stock_check.request` | `order.stock_check.response` | Order → Product inventory |
| `order.payment_status.request` | `order.payment_status.response` | Order → Payment |
| `order.cart_items.request` | `order.cart_items.response` | Order → Product cart |
| `order.address.request` | `order.address.response` | Order → Identity |
| `order.refunds.request` | `order.refunds.response` | Order → Payment |

---

## 📊 Kafka Topic Configuration

### Retention Policies

| Topic | Retention | Use Case |
|-------|-----------|----------|
| account.* | 7 days | Account security events |
| product.* | 30 days | Product lifecycle tracking |
| order.* | 30 days | Order history & audit |
| payment.* | 90 days | Payment compliance & refunds |
| flash_sale.* | 7 days | Session-based events |
| loyalty.* | 365 days | Annual loyalty audit |
| refund.* | 90 days | Financial audit trail |
| appeal.* | 365 days | Legal appeal records |

### Partitioning Strategy

```
Key (Partition By):
- account.* → user_id (same user events go to same partition)
- order.* → order_id (same order events ordered)
- payment.* → transaction_id (payment events sequential)
- product.* → product_id (product events sequential)
- flash_sale.* → session_id (session events ordered)

Default: 3 partitions per topic
Replication Factor: 3 (HA)
```

---

## 🔄 Event Correlation

### Related Event Chains

**Shopping Flow**:
```
order.created → (payment received) → payment.success → order.shipped 
  → order.delivered → loyalty.points_earned
```

**Refund Flow**:
```
refund.requested → (admin review) → refund.admin_approved 
  → (loyalty adjustment) → loyalty.points_refunded
```

**Flash Sale Flow**:
```
flash_sale.session_started → flash_sale.item_approved 
  → flash_sale.item_sold → order.created → payment.success
```

**Trust Score Impact**:
```
order.cancelled → (if excessive) → account.auto_locked 
  → account.locked → seller.posting_suspended
```

---

## 🛠️ Consuming Events (Developer Guide)

### Consumer Group Pattern

```java
// Example: Order Service consuming payment.success

@Service
public class PaymentSuccessConsumer {
  
  @KafkaListener(
    topics = "payment.success",
    groupId = "order-service-payment",
    containerFactory = "kafkaListenerContainerFactory"
  )
  public void onPaymentSuccess(PaymentSuccessEvent event) {
    // 1. Validate event
    if (!isValidEvent(event)) return;
    
    // 2. Retrieve order
    Order order = orderRepository.findById(event.parent_order_id);
    
    // 3. Update status
    order.setStatus("PAID");
    orderRepository.save(order);
    
    // 4. Produce downstream events
    orderEventPublisher.publishOrderPaid(order);
    
    // 5. Send notifications
    notificationService.sendPaymentConfirmation(order);
  }
}
```

### Idempotency Pattern

```java
// Use event_id as idempotency key
@Repository
public class ProcessedEventRepository {
  
  public boolean isProcessed(String eventId) {
    return findByEventId(eventId).isPresent();
  }
  
  public void markProcessed(String eventId) {
    save(new ProcessedEvent(eventId, now()));
  }
}

// In consumer
public void onEvent(KafkaEvent event) {
  if (processedEventRepo.isProcessed(event.event_id)) {
    return; // Already processed, skip
  }
  
  // Process event
  processEvent(event);
  
  // Mark as processed
  processedEventRepo.markProcessed(event.event_id);
}
```

---

## 📈 Monitoring & Observability

### Key Metrics per Topic

```
For each Kafka topic track:
- Messages/sec (throughput)
- Latency (p50, p95, p99)
- Error rate (messages causing exceptions)
- Consumer lag (processing delay)
- Partition skew (load balancing)
```

### Dead Letter Queue Pattern

```json
// If consumer fails 3 times, route to DLQ
{
  "event_id": "evt_20251001_001",
  "original_topic": "order.delivered",
  "original_partition": 0,
  "original_offset": 12345,
  "error_reason": "Loyalty service unavailable",
  "retry_count": 3,
  "dlq_timestamp": "2025-10-01T14:00:00Z",
  "require_manual_intervention": true
}
```

---

## 📋 Event Schema Registry

All events follow this base structure:

```json
{
  "event_id": "evt_YYYYMMDD_NNN",        // Unique identifier
  "event_type": "domain.action",          // Topic name
  "timestamp": "ISO 8601",                // When it happened
  "correlation_id": "uuid",               // Trace across services
  "source_service": "service-name",       // Where it came from
  "version": 1,                           // Schema version
  "data": { /* service-specific */ }     // Actual payload
}
```

---

**Last Updated**: 2026-05-02  
**Version**: v5.4  
**Total Topics**: 41 (29 event + 12 request-reply)
**Status**: Production Ready

