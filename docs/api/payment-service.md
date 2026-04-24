# Payment Service — API Documentation

**Base URL:** `http://{host}:8080/api/v1`
**Authentication:** JWT Bearer token (except webhook endpoints)
**Service Port:** 8082 (internal)

All responses follow the standard `ApiResponse<T>` wrapper. See [Response Format](#response-format) for details.

---

## Response Format

### ApiResponse\<T\>

```json
{
  "success": true,
  "message": "Operation succeeded",
  "data": { ... },
  "errorCode": null,
  "timestamp": 1713964800000
}
```

### PageResponse\<T\> (paginated lists)

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "last": false
}
```

---

## Table of Contents

1. [Payment Endpoints](#1-payment-endpoints) — Buyer-facing payment operations
2. [Stripe Webhook](#2-stripe-webhook) — Inbound webhook (no auth)
3. [Stripe Onboarding](#3-stripe-onboarding) — Seller Stripe Connect onboarding
4. [Admin Refunds](#4-admin-refunds) — Admin refund management

---

## 1. Payment Endpoints

Buyer and Admin use these to query transaction status and retrieve Stripe client secrets.

---

### `GET /payments/parent-order/{parentOrderId}`

Retrieve the full transaction detail for a parent order, including per-seller transfer breakdown.

**Authorization:** `BUYER` or `ADMIN` role required.

**Path Parameters**

| Name | Type | Description |
|------|------|-------------|
| `parentOrderId` | long | Parent order ID |

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "transaction_id": 1001,
    "parent_order_id": 5001,
    "amount": "299.00",
    "method": "stripe",
    "status": "PENDING",
    "stripe_pi_id": "pi_3Ox...",
    "application_fee": "14.95",
    "application_fee_percentage": "5.00",
    "trans_ref": "TXN-20240425-001",
    "paid_at": null,
    "remaining_seconds": 599,
    "sellers": [
      {
        "seller_id": 42,
        "seller_name": "TechGadget Store",
        "order_id": 5002,
        "amount": "199.00",
        "fee": "9.95",
        "net_amount": "189.05",
        "stripe_transfer_id": null,
        "transfer_status": "PENDING"
      }
    ]
  }
}
```

**Field Details**

| Field | Type | Description |
|-------|------|-------------|
| `transaction_id` | long | Internal transaction ID |
| `parent_order_id` | long | Parent order this transaction belongs to |
| `amount` | decimal | Total transaction amount |
| `method` | string | Payment method used (`stripe`) |
| `status` | string | `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED` |
| `stripe_pi_id` | string | Stripe PaymentIntent ID |
| `application_fee` | decimal | Platform fee deducted |
| `application_fee_percentage` | decimal | Platform fee percentage |
| `trans_ref` | string | Internal transaction reference |
| `paid_at` | ISO 8601 | Timestamp when payment succeeded |
| `remaining_seconds` | long | Seconds left to complete payment (only when `PENDING`) |
| `sellers` | array | Per-seller transfer breakdown |

**Status Codes**

| Code | Meaning |
|------|---------|
| `200 OK` | Transaction found |
| `401 Unauthorized` | Missing or invalid JWT |
| `403 Forbidden` | User is not BUYER or ADMIN |
| `404 Not Found` | Transaction not found |

---

### `GET /payments/parent-order/{parentOrderId}/client-secret`

Retrieve the Stripe PaymentIntent `client_secret` for frontend payment confirmation (Stripe Elements / Payment Sheet).

**Authorization:** `BUYER` role required.

**Path Parameters**

| Name | Type | Description |
|------|------|-------------|
| `parentOrderId` | long | Parent order ID |

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "parent_order_id": 5001,
    "transaction_id": 1001,
    "client_secret": "pi_3Ox..._secret_abc...",
    "status": "PENDING"
  }
}
```

**Pre-conditions**

- Transaction status must be `PENDING`.
- Payment page must be loaded within the payment window (e.g., 10 minutes).

**Status Codes**

| Code | Meaning |
|------|---------|
| `200 OK` | Client secret retrieved |
| `400 Bad Request` | Transaction not in PENDING state |
| `401 Unauthorized` | Missing or invalid JWT |
| `403 Forbidden` | User is not BUYER |
| `404 Not Found` | Transaction not found |

---

## 2. Stripe Webhook

> **No JWT authentication.** This endpoint is authenticated via the `Stripe-Signature` header.

---

### `POST /stripe/webhooks`

Receive and process Stripe webhook events. All events are validated against the configured webhook secret before processing.

**Headers**

| Name | Required | Description |
|------|----------|-------------|
| `Stripe-Signature` | Yes | Stripe webhook signature (HMAC SHA256) |
| `Content-Type` | Yes | `application/json` |

**Request Body** — Raw Stripe event JSON (forwarded directly)

**Events Handled**

| Stripe Event | Action |
|--------------|--------|
| `payment_intent.succeeded` | Set transaction status to `SUCCESS` |
| `payment_intent.payment_failed` | Set transaction status to `FAILED` |
| `charge.refunded` | Publish `refund.stripe_auto` event via Kafka |
| `account.updated` | Sync Stripe Express account status |
| `transfer.created` | Record `stripe_transfer_id` on seller transfer record |

**Response** `200 OK`

```json
"received"
```

**Status Codes**

| Code | Meaning |
|------|---------|
| `200 OK` | Event received and validated |
| `400 Bad Request` | Signature validation failed |

---

## 3. Stripe Onboarding

Seller Stripe Connect onboarding — required for sellers to receive payments.

---

### `POST /stripe/onboarding/start`

Start a new Stripe Connect Express onboarding flow. Creates a Stripe Express account and returns an onboarding link.

**Authorization:** `SELLER` role required.

**Response** `201 Created`

```json
{
  "success": true,
  "message": "Stripe onboarding started",
  "data": {
    "onboarding_url": "https://connect.stripe.com/express/oauth/authorize?...",
    "stripe_account_id": "acct_1Ox...",
    "expires_at": "2024-04-26T10:00:00Z"
  }
}
```

**Side Effects**

- Creates a new `SELLER_STRIPE_ACCOUNTS` record (status `PENDING`).
- If an account already exists and is incomplete, reuses it and generates a fresh AccountLink.

**Status Codes**

| Code | Meaning |
|------|---------|
| `201 Created` | Onboarding started |
| `401 Unauthorized` | Missing or invalid JWT |
| `403 Forbidden` | User is not SELLER |
| `409 Conflict` | Stripe account already fully onboarded |

---

### `GET /stripe/onboarding/status`

Check the current Stripe Connect account status for the authenticated seller.

**Authorization:** `SELLER` role required.

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "stripe_account_id": "acct_1Ox...",
    "account_status": "RESTRICTED",
    "details_submitted": true,
    "charges_enabled": false,
    "payouts_enabled": false,
    "onboarding_status": "IN_PROGRESS",
    "onboarding_url": "https://connect.stripe.com/express/reauth?..."
  }
}
```

**Onboarding Status Values**

| Value | Meaning |
|-------|---------|
| `PENDING` | Onboarding not started |
| `IN_PROGRESS` | Seller has started but not completed |
| `COMPLETE` | Fully onboarded — can receive payouts |
| `SUSPENDED` | Account suspended by Stripe |

**Status Codes**

| Code | Meaning |
|------|---------|
| `200 OK` | Status retrieved |
| `404 Not Found` | No Stripe account found for this seller |

---

### `POST /stripe/onboarding/refresh-link`

Generate a new onboarding link when the previous one has expired.

**Authorization:** `SELLER` role required.

**Response** `200 OK`

```json
{
  "success": true,
  "message": "Onboarding link refreshed",
  "data": {
    "onboarding_url": "https://connect.stripe.com/express/oauth/authorize?...",
    "stripe_account_id": "acct_1Ox...",
    "expires_at": "2024-04-26T12:00:00Z"
  }
}
```

**Pre-conditions**

- Seller must have an existing Stripe account record.
- Only works when the account is not yet fully onboarded.

**Status Codes**

| Code | Meaning |
|------|---------|
| `200 OK` | New link generated |
| `404 Not Found` | No Stripe account found |
| `409 Conflict` | Account already fully onboarded |

---

## 4. Admin Refunds

Admin-only endpoints for managing refund requests across the platform.

---

### `GET /admin/refunds`

List all refund requests with optional filters and pagination.

**Authorization:** `ADMIN` role required.

**Query Parameters**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `status` | string | No | `PENDING`, `SUCCESS`, `FAILED`, `REJECTED` |
| `type` | string | No | `FULL`, `PARTIAL` |
| `from_date` | string | No | Start date filter `yyyy-MM-dd` |
| `to_date` | string | No | End date filter `yyyy-MM-dd` |
| `page` | int | No | Page number (default `0`) |
| `size` | int | No | Page size (default `20`) |

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "refund_id": 301,
        "refund_code": "RFD-20240425-001",
        "order_id": 5002,
        "group_ref": "550e8400-e29b-41d4-a716-446655440000",
        "type": "PARTIAL",
        "status": "PENDING",
        "amount": "99.00",
        "adjust_amount": null,
        "initiated_by": "BUYER",
        "refund_reason_type": "DEFECTIVE",
        "admin_note": null,
        "reject_reason": null,
        "reviewed_by": null,
        "reviewed_at": null,
        "refund_ref": null,
        "created_at": "2024-04-25T08:30:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 45,
    "totalPages": 3,
    "last": false
  }
}
```

**Refund Type Values**

| Value | Description |
|-------|-------------|
| `FULL` | Full order refund |
| `PARTIAL` | Partial refund for specific items |

**Refund Status Values**

| Value | Description |
|-------|-------------|
| `PENDING` | Awaiting admin review |
| `SUCCESS` | Refund processed successfully |
| `FAILED` | Stripe refund failed |
| `REJECTED` | Admin rejected the request |

**Status Codes**

| Code | Meaning |
|------|---------|
| `200 OK` | List retrieved |
| `401 Unauthorized` | Missing or invalid JWT |
| `403 Forbidden` | User is not ADMIN |

---

### `GET /admin/refunds/{refundId}`

Get full details of a single refund request, including items, evidence images, and review history.

**Authorization:** `ADMIN` role required.

**Path Parameters**

| Name | Type | Description |
|------|------|-------------|
| `refundId` | long | Refund request ID |

**Response** `200 OK`

```json
{
  "success": true,
  "data": {
    "refund_id": 301,
    "refund_code": "RFD-20240425-001",
    "order_id": 5002,
    "group_ref": "550e8400-e29b-41d4-a716-446655440000",
    "type": "PARTIAL",
    "status": "PENDING",
    "amount": "99.00",
    "adjust_amount": null,
    "reason": "Product arrived damaged",
    "initiated_by": "BUYER",
    "refund_reason_type": "DEFECTIVE",
    "evidence_images": [
      "https://storage.example.com/evidence/img1.jpg",
      "https://storage.example.com/evidence/img2.jpg"
    ],
    "admin_note": null,
    "reject_reason": null,
    "caused_by": "SELLER",
    "tracking_number": null,
    "return_evidence": [],
    "reviewed_by": null,
    "reviewed_at": null,
    "stripe_refund_id": null,
    "items": [
      {
        "item_id": 101,
        "quantity": 1,
        "refund_amount": "99.00",
        "item_reason": "DEFECTIVE",
        "status": "PENDING",
        "return_tracking_number": null,
        "returned_at": null
      }
    ],
    "created_at": "2024-04-25T08:30:00Z",
    "updated_at": "2024-04-25T08:30:00Z"
  }
}
```

**Caused By Values**

| Value | Description |
|-------|-------------|
| `SELLER` | Seller at fault (affects seller trust score) |
| `BUYER` | Buyer at fault (fraud flag) |
| `LOGISTICS` | Shipping carrier at fault |
| `SYSTEM` | Platform system error |

**Status Codes**

| Code | Meaning |
|------|---------|
| `200 OK` | Refund detail retrieved |
| `404 Not Found` | Refund not found |

---

### `POST /admin/refunds/{refundId}/approve`

Approve a refund request. Executes the Stripe refund and updates all related records.

**Authorization:** `ADMIN` role required.

**Path Parameters**

| Name | Type | Description |
|------|------|-------------|
| `refundId` | long | Refund request ID |

**Request Body**

```json
{
  "admin_note": "Approved after evidence review. Defective item confirmed.",
  "adjust_amount": "95.00",
  "caused_by": "SELLER",
  "tracking_number": "VN123456789"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `admin_note` | string | **Yes** | Admin note (1–1000 chars) |
| `adjust_amount` | decimal | No | Override refund amount (e.g., deduct restocking fee) |
| `caused_by` | string | No | `SELLER`, `BUYER`, `LOGISTICS`, `SYSTEM` |
| `tracking_number` | string | No | Return shipping tracking number (format: `XX123456789`) |

**Response** `200 OK`

```json
{
  "success": true,
  "message": "Refund approved successfully",
  "data": {
    "refund_id": 301,
    "refund_code": "RFD-20240425-001",
    "status": "SUCCESS",
    "type": "PARTIAL",
    "amount": "99.00",
    "adjust_amount": "95.00",
    "tracking_number": "VN123456789",
    "return_evidence": [
      {
        "type": "TRACKING_NUMBER",
        "tracking_number": "VN123456789",
        "recorded_at": "2024-04-25T10:00:00Z"
      }
    ],
    "reviewed_by": 1,
    "admin_note": "Approved after evidence review.",
    "reviewed_at": "2024-04-25T10:00:00Z",
    "stripe_refund_id": "re_3Ox..."
  }
}
```

**Side Effects**

1. Calls `Stripe refunds.create` (with `adjust_amount` if provided).
2. Updates `REFUNDS.status = SUCCESS`.
3. If `tracking_number` is provided, updates `REFUND_ITEMS` records.
4. Publishes `refund.admin_approved` Kafka event (triggers notification to buyer).
5. If `caused_by = SELLER`, decreases seller trust score by 5 (via Kafka → identity-service).

**Status Codes**

| Code | Meaning |
|------|---------|
| `200 OK` | Refund approved and processed |
| `400 Bad Request` | Invalid request body or already processed |
| `404 Not Found` | Refund not found |
| `409 Conflict` | Refund not in `PENDING` state |

---

### `POST /admin/refunds/{refundId}/reject`

Reject a refund request.

**Authorization:** `ADMIN` role required.

**Path Parameters**

| Name | Type | Description |
|------|------|-------------|
| `refundId` | long | Refund request ID |

**Request Body**

```json
{
  "reject_reason": "Evidence insufficient. Product damage not verified.",
  "fraud_evidence": false
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `reject_reason` | string | **Yes** | Reason for rejection |
| `fraud_evidence` | boolean | No | Set `true` if fraud suspected (default `false`) |

**Response** `200 OK`

```json
{
  "success": true,
  "message": "Refund rejected",
  "data": null
}
```

**Side Effects**

1. Updates `REFUNDS.status = REJECTED` and records `reject_reason`.
2. Publishes `refund.rejected` Kafka event (triggers notification to buyer).
3. If `fraud_evidence = true`, flags the buyer (trust score impact via Kafka → identity-service).

**Status Codes**

| Code | Meaning |
|------|---------|
| `200 OK` | Refund rejected |
| `400 Bad Request` | Missing reject_reason |
| `404 Not Found` | Refund not found |
| `409 Conflict` | Refund not in `PENDING` state |

---

## Appendix: Common Error Codes

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `UNAUTHORIZED` | 401 | Missing or invalid JWT token |
| `FORBIDDEN` | 403 | Insufficient role permissions |
| `NOT_FOUND` | 404 | Resource not found |
| `CONFLICT` | 409 | State conflict (e.g., already processed) |
| `BAD_REQUEST` | 400 | Invalid request parameters |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## Appendix: Role Requirements Summary

| Endpoint | BUYER | SELLER | ADMIN | None |
|----------|-------|--------|-------|------|
| `GET /payments/parent-order/{id}` | Yes | No | Yes | No |
| `GET /payments/parent-order/{id}/client-secret` | Yes | No | No | No |
| `POST /stripe/webhooks` | No | No | No | Yes (Stripe sig) |
| `POST /stripe/onboarding/start` | No | Yes | No | No |
| `GET /stripe/onboarding/status` | No | Yes | No | No |
| `POST /stripe/onboarding/refresh-link` | No | Yes | No | No |
| `GET /admin/refunds` | No | No | Yes | No |
| `GET /admin/refunds/{id}` | No | No | Yes | No |
| `POST /admin/refunds/{id}/approve` | No | No | Yes | No |
| `POST /admin/refunds/{id}/reject` | No | No | Yes | No |
