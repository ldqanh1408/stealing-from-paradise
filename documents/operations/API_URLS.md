# API Endpoints Reference

> **Source**: documents/operations/API_URLS.md
> **Generated**: 2026-05-10
> **Base path**: `/api/v1` -- Gateway stripPrefix(1) -- `/{service}:{port}`

---

## Identity Service (`identity-service:8081`)

### Public

| Method | Path | Notes |
|--------|------|-------|
| POST | /auth/register | Register |
| POST | /auth/login | Login, returns JWT |
| POST | /auth/refresh | Refresh token |

### JWT Required

| Method | Path | Notes |
|--------|------|-------|
| POST | /auth/logout | Revoke token |
| GET | /users/me | My profile |
| PUT | /users/me | Update profile |
| GET | /users/me/avatar/presigned-url | Upload avatar |
| GET | /users/me/addresses | My addresses |
| POST | /users/me/addresses | Add address |
| PUT | /users/me/addresses/{addressId} | Edit address |
| DELETE | /users/me/addresses/{addressId} | Delete address |

### Admin (JWT + ADMIN)

| Method | Path | Notes |
|--------|------|-------|
| GET | /admin/users | List users |

---

## Product Service (`product-service:8090`)

### Public

| Method | Path | Notes |
|--------|------|-------|
| GET | /products | Product list |
| GET | /products/{productId} | Product detail |
| GET | /categories | Category list |

### JWT Required

| Method | Path | Notes |
|--------|------|-------|
| GET | /cart | View cart |
| POST | /cart/items | Add to cart |
| PUT | /cart/items/{itemId} | Update quantity |
| DELETE | /cart/items/{itemId} | Remove item |
| DELETE | /cart | Clear cart |
| GET | /inventory/{skuCode} | Stock check |

### Seller (JWT + SELLER)

| Method | Path | Notes |
|--------|------|-------|
| POST | /products | Create product |
| PUT | /products/{productId} | Edit product |
| DELETE | /products/{productId} | Soft-delete |
| GET | /products/{productId}/presigned-url | Upload image |
| GET | /sellers/me/products | My products |
| GET | /seller/products/{productId}/variants | Variant list |
| POST | /seller/products/{productId}/variants | Add variant |
| PUT | /seller/variants/{variantId} | Edit variant |
| DELETE | /seller/variants/{variantId} | Delete variant |
| POST | /seller/products/{productId}/submit | Submit for review |
| POST | /seller/products/{productId}/publish | Publish |
| POST | /seller/products/{productId}/unpublish | Unpublish |
| PUT | /inventory/{skuCode}/restock | Restock |
| POST | /seller/inventory/adjust | Adjust inventory |
| GET | /seller/inventory/{skuCode}/logs | Inventory log |

### Admin (JWT + ADMIN)

| Method | Path | Notes |
|--------|------|-------|
| GET | /admin/products/pending | Pending products |
| POST | /admin/products/{productId}/approve | Approve |
| POST | /admin/products/{productId}/reject | Reject |
| POST | /admin/categories | Create category |
| PUT | /admin/categories/{categoryId} | Edit category |
| DELETE | /admin/categories/{categoryId} | Delete category |

---

## Order Service (`order-service:8083`)

### Buyer (JWT + BUYER)

| Method | Path | Notes |
|--------|------|-------|
| POST | /orders/checkout | Create order (multi-vendor) |
| GET | /orders | My orders |
| GET | /orders/{orderId} | Order detail |
| GET | /orders/parent/{parentOrderId} | Parent order detail |
| POST | /orders/{orderId}/cancel | Cancel order |
| POST | /orders/{orderId}/confirm-received | Confirm delivery |
| POST | /orders/{orderId}/return-to-sender | Return to sender |
| POST | /orders/{orderId}/refunds | Partial refund |
| POST | /orders/parent/{parentOrderId}/refund | Full refund |
| POST | /orders/parent/{parentOrderId}/refunds/partial | Multi-seller partial |
| GET | /orders/parent/{parentOrderId}/refund | Full refund status |
| GET | /orders/refunds | Refund requests |
| GET | /orders/{orderId}/refunds | Order refund history |
| GET | /orders/{orderId}/refunds/{refundId} | Refund detail |
| GET | /orders/{orderId}/refunds/presigned-url | Upload evidence |

### Seller (JWT + SELLER)

| Method | Path | Notes |
|--------|------|-------|
| GET | /orders/{orderId} | Order detail |
| GET | /sellers/me/orders | My orders |
| PUT | /orders/{orderId}/tracking | Update tracking number |
| GET | /orders/{orderId}/refunds | Refund history |

### Admin (JWT + ADMIN)

| Method | Path | Notes |
|--------|------|-------|
| GET | /orders/parent/{parentOrderId} | Parent order detail |
| GET | /orders/parent/{parentOrderId}/refund | Refund status |
| GET | /orders/{orderId}/refunds | Refund history |

---

## Payment Service (`payment-service:8082`)

### Public

| Method | Path | Notes |
|--------|------|-------|
| POST | /stripe/webhooks | Stripe webhook (Stripe signature) |

### Seller (JWT + SELLER)

| Method | Path | Notes |
|--------|------|-------|
| POST | /stripe/onboarding/start | Start KYC |
| GET | /stripe/onboarding/status | KYC status |
| POST | /stripe/onboarding/refresh-link | Refresh link |
| GET | /seller/payments/transfers | Transfer history |
| GET | /seller/payments/balance | Available balance |

### Buyer/Admin (JWT)

| Method | Path | Notes |
|--------|------|-------|
| GET | /payments/parent-order/{parentOrderId} | Payment status |
| GET | /payments/by-intent/{stripePaymentIntentId} | Lookup by PaymentIntent |

---

## Refund Service (`refund-service:8094`)

### Admin (JWT + ADMIN)

| Method | Path | Notes |
|--------|------|-------|
| GET | /admin/refunds | Pending refunds |
| POST | /admin/refunds/{refundId}/approve | Approve refund |
| POST | /admin/refunds/{refundId}/reject | Reject refund |

---

## Flash Sale Service (`flashsale-service:8085`)

### Public

| Method | Path | Notes |
|--------|------|-------|
| GET | /flash-sale/sessions | Session list |
| GET | /flash-sale/sessions/{sessionId} | Session detail + items |

### Buyer (JWT + BUYER)

| Method | Path | Notes |
|--------|------|-------|
| POST | /flash-sale/sessions/{sessionId}/buy | Purchase flash item |
| POST | /flash-sale/sessions/{sessionId}/reminders | Register reminder |

### JWT Required

| Method | Path | Notes |
|--------|------|-------|
| DELETE | /flash-sale/sessions/{sessionId}/reminders | Cancel reminder |

### Seller (JWT + SELLER)

| Method | Path | Notes |
|--------|------|-------|
| POST | /flash-sale/sessions/{sessionId}/items | Register product |

### Admin (JWT + ADMIN)

| Method | Path | Notes |
|--------|------|-------|
| POST | /flash-sale/sessions | Create session |
| GET | /admin/flash-sale/sessions | All sessions |
| PUT | /admin/flash-sale/sessions/{sessionId} | Edit session |
| DELETE | /admin/flash-sale/sessions/{sessionId} | Delete session |

---

## Search Service (`search-service:8091`)

### Public

| Method | Path | Notes |
|--------|------|-------|
| GET | /search/products | Full-text search |
| GET | /search/products/suggest | Autocomplete |

---

## Notification Service (`notification-service:8092`)

### JWT Required

| Method | Path | Notes |
|--------|------|-------|
| GET | /notifications/stream | SSE real-time stream |
| GET | /notifications | Notification list |
| PATCH | /notifications/{notifId}/read | Mark as read |
| PATCH | /notifications/read-all | Mark all as read |
| GET | /notifications/unread-count | Unread count |

---

## AI Chat Service (`ai-chat-service:8093`)

> Base path: `/api/ai` -- Gateway routes to `ai-chat-service`

### Public (Optional JWT)

| Method | Path | Notes |
|--------|------|-------|
| GET | /api/ai/suggest | Quick question suggestions |

### JWT Required

| Method | Path | Notes |
|--------|------|-------|
| POST | /api/ai/chat | Chat streaming (SSE) |
| GET | /api/ai/chat/history | Conversation history |
| POST | /api/ai/sessions | Create new session |
| DELETE | /api/ai/sessions/{sessionId} | Close session |
| POST | /api/ai/confirm | Confirm/reject Level 3 action |

---

## Summary by Role

| Service | Public | JWT | Seller | Buyer | Admin | Total |
|---------|--------|-----|--------|-------|-------|-------|
| Identity | 3 | 12 | -- | -- | 3 | 18 |
| Product | 3 | 5 | 16 | -- | 6 | 30 |
| Order | 0 | -- | 4 | 15 | 3 | 22 |
| Payment | 1 | 2 | 5 | -- | 3 | 11 |
| Flash Sale | 2 | 1 | 1 | 2 | 6 | 12 |
| Search | 2 | -- | -- | -- | -- | 2 |
| Notification | -- | 5 | -- | -- | -- | 5 |
| AI Chat | 1 | 5 | -- | -- | -- | 6 |
| **Total** | **12** | **29** | **26** | **17** | **27** | **111** |
