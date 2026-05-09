# Traceability Matrix — Order Service

**Document ID:** TRACE-ORDER-001
**Service:** order-service (port 8083)
**Last Updated:** 2026-05-09

---

## Matrix: FR → BR → UC → Entity → API → State

| FR ID | Description | BR IDs | UC IDs | Entity IDs | API Contract | State Transition |
|-------|-------------|--------|--------|------------|-------------|------------------|
| FR-ORDER-001 | Multi-Vendor Checkout | BR-001..009 | UC-ORDER-001 | ENTITY-001,002,003 | api-post-orders-checkout.yaml | → PENDING |
| FR-ORDER-002 | Stock Validation (Req-Rep) | BR-ORDER-002 | UC-ORDER-001 | ENTITY-003 | — | — |
| FR-ORDER-003 | Address Validation (Req-Rep) | BR-ORDER-003 | UC-ORDER-001 | ENTITY-002 | — | — |
| FR-ORDER-004 | Order Code Generation | BR-ORDER-006 | — | ENTITY-002 | — | — |
| FR-ORDER-005 | Buyer Order Listing | — | UC-ORDER-002 | ENTITY-002 | api-get-orders.yaml | — |
| FR-ORDER-006 | Order Detail | — | UC-ORDER-002 | ENTITY-002,003 | api-get-orders.yaml | — |
| FR-ORDER-007 | Parent Order Detail | — | UC-ORDER-002 | ENTITY-001,002 | — | — |
| FR-ORDER-008 | Buyer Cancel Order | BR-011,021,025 | UC-ORDER-003 | ENTITY-002 | api-post-orders-return.yaml | PENDING→CANCELLED |
| FR-ORDER-009 | Seller Update Tracking | BR-ORDER-013 | UC-ORDER-004 | ENTITY-002 | api-put-orders-ship.yaml | PAID→SHIPPING |
| FR-ORDER-010 | Buyer Confirm Delivery | BR-ORDER-014 | UC-ORDER-005 | ENTITY-002 | api-put-orders-ship.yaml | SHIPPING→DELIVERED |
| FR-ORDER-011 | Auto-Confirm Delivery (JOB-22) | BR-ORDER-015 | — | ENTITY-002 | — | SHIPPING→DELIVERED |
| FR-ORDER-012 | Seller RTS | BR-016,022 | UC-ORDER-006 | ENTITY-002 | api-post-orders-return.yaml | SHIPPING→RETURNED |
| FR-ORDER-013 | Buyer Refund Request | BR-017,018,019 | UC-ORDER-006 | ENTITY-002 | api-post-orders-return.yaml | DELIVERED→REFUNDED/PARTIALLY_REFUNDED |
| FR-ORDER-014 | Seller Order Listing | — | UC-ORDER-007 | ENTITY-002 | api-get-orders.yaml | — |
| FR-ORDER-015 | Seller Dashboard | — | — | ENTITY-002 | api-get-orders.yaml | — |
| FR-ORDER-016 | Kafka Event Production | BR-009..016 | — | — | — | ALL |
| FR-ORDER-017 | Kafka Event Consumption | — | — | — | — | PENDING→PAID, etc. |
| FR-ORDER-018 | Saga Orchestration (Axon) | — | UC-ORDER-001 | — | — | Payment lifecycle |

---

## Matrix: Use Case → FR → API → State

| UC ID | Use Case | Actor | FR IDs | API | State Change |
|-------|----------|-------|--------|-----|-------------|
| UC-ORDER-001 | Checkout | BUYER | FR-001,002,003,004 | POST /orders/checkout | → PENDING |
| UC-ORDER-002 | View Orders | BUYER | FR-005,006,007 | GET /orders, /orders/{id}, /orders/parent/{id} | None (read) |
| UC-ORDER-003 | Cancel Order | BUYER/SELLER | FR-ORDER-008 | POST /orders/{id}/cancel | PENDING→CANCELLED |
| UC-ORDER-004 | Ship Order | SELLER | FR-ORDER-009 | PUT /orders/{id}/tracking | PAID→SHIPPING |
| UC-ORDER-005 | Confirm Delivery | BUYER | FR-010,011 | POST /orders/{id}/confirm-received | SHIPPING→DELIVERED |
| UC-ORDER-006 | Request Return | BUYER/SELLER | FR-012,013 | POST /orders/{id}/return-to-sender, POST /orders/{id}/refunds | SHIPPING→RETURNED, DELIVERED→REFUNDED |
| UC-ORDER-007 | View Seller Orders | SELLER | FR-014,015 | GET /sellers/me/orders, GET /sellers/me/dashboard | None (read) |

---

## Matrix: Business Rule → Entity → State

| BR ID | Rule Summary | Entity | State Transition |
|-------|-------------|--------|-----------------|
| BR-ORDER-001 | Cart not empty | ENTITY-003 | — |
| BR-ORDER-002 | Stock availability check | ENTITY-003 | — |
| BR-ORDER-003 | Address validation | ENTITY-002 | — |
| BR-ORDER-004 | Multi-vendor split | ENTITY-001,002 | → PENDING |
| BR-ORDER-005 | Parent financial integrity | ENTITY-001,002 | — |
| BR-ORDER-006 | Order code generation | ENTITY-002 | — |
| BR-ORDER-007 | Shipping deadline (3 days) | ENTITY-002 | — |
| BR-ORDER-008 | Checkout idempotency | ENTITY-001 | — |
| BR-ORDER-009 | Kafka event on checkout | — | → PENDING |
| BR-ORDER-010 | PENDING→PAID | ENTITY-002 | PENDING→PAID |
| BR-ORDER-011 | PENDING→CANCELLED | ENTITY-002 | PENDING→CANCELLED |
| BR-ORDER-012 | Auto-cancel (JOB-13) | ENTITY-002 | PENDING→CANCELLED |
| BR-ORDER-013 | PAID→SHIPPING | ENTITY-002 | PAID→SHIPPING |
| BR-ORDER-014 | SHIPPING→DELIVERED (buyer) | ENTITY-002 | SHIPPING→DELIVERED |
| BR-ORDER-015 | SHIPPING→DELIVERED (auto) | ENTITY-002 | SHIPPING→DELIVERED |
| BR-ORDER-016 | SHIPPING→RETURNED (RTS) | ENTITY-002 | SHIPPING→RETURNED |
| BR-ORDER-017 | Return window (7 days) | ENTITY-002 | — |
| BR-ORDER-018 | DELIVERED→REFUNDED | ENTITY-002 | DELIVERED→REFUNDED |
| BR-ORDER-019 | DELIVERED→PARTIALLY_REFUNDED | ENTITY-002 | DELIVERED→PARTIALLY_REFUNDED |
| BR-ORDER-020 | RETURNED→REFUNDED (auto) | ENTITY-002 | RETURNED→REFUNDED |
| BR-ORDER-021 | Cancellation actor rules | ENTITY-002 | — |
| BR-ORDER-022 | RTS vs Buyer Refund | ENTITY-002 | — |
| BR-ORDER-023 | Parent status sync | ENTITY-001,002 | — |
| BR-ORDER-024 | Immutable shipping snapshot | ENTITY-002 | — |
| BR-ORDER-025 | Stock reservation release | ENTITY-003 | — |

---

## Matrix: API Contract → Operations

| API Contract File | Method | Path | Auth | UC |
|-------------------|--------|------|------|----|
| api-post-orders-checkout.yaml | POST | /orders/checkout | BUYER | UC-ORDER-001 |
| api-get-orders.yaml | GET | /orders | BUYER | UC-ORDER-002 |
| api-get-orders.yaml | GET | /orders/{id} | BUYER\|SELLER | UC-ORDER-002 |
| api-get-orders.yaml | GET | /orders/parent/{id} | BUYER | UC-ORDER-002 |
| api-get-orders.yaml | GET | /sellers/me/orders | SELLER | UC-ORDER-007 |
| api-get-orders.yaml | GET | /sellers/me/dashboard | SELLER | UC-ORDER-007 |
| api-put-orders-ship.yaml | PUT | /orders/{id}/tracking | SELLER | UC-ORDER-004 |
| api-put-orders-ship.yaml | POST | /orders/{id}/confirm-received | BUYER | UC-ORDER-005 |
| api-post-orders-return.yaml | POST | /orders/{id}/cancel | BUYER\|SELLER | UC-ORDER-003 |
| api-post-orders-return.yaml | POST | /orders/{id}/return-to-sender | SELLER | UC-ORDER-006 |
| api-post-orders-return.yaml | POST | /orders/{id}/refunds | BUYER | UC-ORDER-006 |
| api-post-orders-return.yaml | POST | /orders/parent/{id}/refund | BUYER | UC-ORDER-006 |

---

## Matrix: Kafka Events → Producers / Consumers

| Event | Producer | Consumers | Related State |
|-------|----------|-----------|---------------|
| `order.created` | order-service | product-service, search-service | → PENDING |
| `order.paid` | order-service | — | PENDING→PAID |
| `order.shipped` | order-service | notification-service | PAID→SHIPPING |
| `order.delivered` | order-service | payment-service, notification-service | SHIPPING→DELIVERED |
| `order.cancelled` | order-service | product-service, notification-service | PENDING→CANCELLED |
| `order.returned` | order-service | payment-service, product-service, notification-service | SHIPPING→RETURNED |
| `order.auto_cancelled` | order-service (JOB-13) | product-service, notification-service | PENDING→CANCELLED |
| `order.checkout_completed` | order-service | product-service (cart) | → PENDING |
| `payment.success` | payment-service | order-service | → PAID |
| `payment.failed` | payment-service | order-service | (retry/stay PENDING) |
| `refund.rts_completed` | payment-service | order-service | RETURNED→REFUNDED |
| `refund.admin_approved` | payment-service | order-service | → REFUNDED/PARTIALLY_REFUNDED |

---

## Document Inventory

| Category | Files | Count |
|----------|-------|-------|
| Entity | entity-parent-order.md, entity-order.md, entity-order-item.md | 3 |
| Business Rules | br-checkout.md, br-order-lifecycle.md | 2 |
| Functional Requirements | fr-order.md | 1 |
| Use Cases | uc-001..007 | 7 |
| API Contracts | api-post-orders-checkout.yaml, api-get-orders.yaml, api-put-orders-ship.yaml, api-post-orders-return.yaml | 4 |
| State Diagrams | state-order.md | 1 |
| Traceability | traceability-matrix.md | 1 |
| **Total** | | **19** |

---

## Cross-References

- **Entities:** [data-models/order-service/](../data-models/order-service/)
- **Business Rules:** [business-rules/order-service/](../business-rules/order-service/)
- **Functional Requirements:** [srs/fr/order-service/](../srs/fr/order-service/)
- **Use Cases:** [use-cases/order-service/](../use-cases/order-service/)
- **API Contracts:** [api-contracts/order-service/](../api-contracts/order-service/)
- **State Diagrams:** [state-diagrams/order-service/](../state-diagrams/order-service/)
