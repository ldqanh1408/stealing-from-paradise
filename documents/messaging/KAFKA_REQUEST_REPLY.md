# Kafka Request-Reply Pattern

**Project:** stealing-from-paradise
**Last Updated:** 2026-06-09

---

## Overview

The backend uses Kafka request-reply only for synchronous-like internal reads that still need loose service coupling. Each request includes a `correlation_id`; the responder copies it to the response so the requester can complete the pending future.

---

## Active Request-Reply Pairs

| # | Request Topic | Response Topic | Requester | Responder | Purpose |
|---|---------------|----------------|-----------|-----------|---------|
| 1 | `order.address.request` | `order.address.response` | Product / Flash Sale | Identity | Validate buyer shipping address |
| 2 | `order.refunds.request` | `order.refunds.response` | Order | Refund | Read refund list/detail/status |
| 3 | `order.refund_presigned_url.request` | `order.refund_presigned_url.response` | Order | Refund | Get refund evidence upload URL |
| 4 | `order.payment_status.request` | `order.payment_status.response` | Order | Refund | Check refund/payment status used by refund views |
| 5 | `search.index_data.request` | `search.index_data.response` | Search | Product | Fetch product/category/SKU data for indexing |

---

## Address Validation

Used by product checkout submit and flash-sale buy flows.

```json
{
  "correlation_id": "uuid",
  "user_id": 42,
  "address_id": 5
}
```

Response:

```json
{
  "correlation_id": "uuid",
  "addressId": 5,
  "userId": 42,
  "fullAddress": "123 Le Van Viet",
  "provinceId": 79,
  "districtId": 769,
  "error": false
}
```

---

## Refund Reads

Order Service asks Refund Service for buyer/admin refund views.

```json
{
  "correlation_id": "uuid",
  "order_id": 1001
}
```

Response contains a `refunds[]` list or an error flag.

---

## Search Index Data

Search Service requests product-side indexing pages or field snapshots.

| requestType | Purpose |
|-------------|---------|
| `ACTIVE_PRODUCTS_PAGE` | Full reindex page |
| `PRODUCT_SKU_DOCUMENTS` | All SKU documents for one product |
| `PRODUCT_SEARCH_FIELDS` | Product fields for update events |
| `CATEGORY_SEARCH_FIELDS` | Category fields for update events |

---

## Deprecated / Removed Pairs

| Pair | Reason |
|------|--------|
| `order.stock_check.request` / `order.stock_check.response` | Checkout stock validation moved into Product Service preview/submit |
| `order.cart_items.request` / `order.cart_items.response` | Checkout item snapshots are emitted by Product Service in `order.checkout_submitted` |
| `cart.product_info.request` / `cart.product_info.response` | No backend producer/consumer remains |

---

## Cross-References

- [KAFKA_CATALOG.md](KAFKA_CATALOG.md)
- [Checkout BR](../business-rules/order-service/br-checkout.md)
- [Product checkout flow](../flows/product-service/flow-product-catalog-cart-review.md)
