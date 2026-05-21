---
title: "ERD Diagrams — Per-Service Full & Compact"
date: "2026-05-15"
source: "ERD_FULL_SYSTEM.md · database-entities.md"
---

# ERD DIAGRAMS — FULL PER SERVICE & COMPACT

---

## PHẦN I: ERD ĐẦY ĐỦ THEO TỪNG SERVICE

---

### 1. Identity Service (PostgreSQL · port 8081)

Tables: `USERS` · `ROLES` · `CUSTOMERS` · `SELLERS` · `ADMINS` · `ADDRESSES`

```mermaid
erDiagram
    USERS ||--o{ ROLES        : "1-N | one user, many roles"
    USERS ||--o| CUSTOMERS    : "1-0..1 | one user, one buyer profile"
    USERS ||--o| SELLERS      : "1-0..1 | one user, one seller profile"
    USERS ||--o| ADMINS       : "1-0..1 | one user, one admin profile"
    USERS ||--o{ ADDRESSES    : "1-N | one user, many addresses"

    USERS {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar phone UK
        varchar password "bcrypt hash"
        varchar full_name
        varchar status "ACTIVE / LOCKED"
        varchar role "BUYER / SELLER / ADMIN"
        int version "optimistic lock"
        timestamp created_at
        timestamp updated_at
    }

    ROLES {
        bigint id PK
        bigint user_id FK "CASCADE DELETE"
        varchar role_name "BUYER / SELLER / ADMIN"
        timestamp created_at
        timestamp updated_at
    }

    CUSTOMERS {
        bigint id PK
        bigint user_id FK "UNIQUE"
        timestamp created_at
        timestamp updated_at
    }

    SELLERS {
        bigint id PK
        bigint user_id FK "UNIQUE"
        timestamp created_at
        timestamp updated_at
    }

    ADMINS {
        bigint id PK
        bigint user_id FK "UNIQUE"
        timestamp created_at
        timestamp updated_at
    }

    ADDRESSES {
        bigint id PK
        bigint user_id FK "CASCADE DELETE"
        int province_id
        int district_id
        text full_address
        boolean is_default "DEFAULT false"
        timestamp created_at
        timestamp updated_at
    }
```

> **Cross-service soft-refs (no hard FK):**
> `CUSTOMERS.id` ← CART.customer_id, PARENT_ORDERS.customer_id
> `SELLERS.id` ← ORDERS.seller_id, SELLER_STRIPE_ACCOUNTS.seller_id, SELLER_TRANSFERS.seller_id

---

### 2. Product Service — Catalog Domain (PostgreSQL · port 8090)

Tables: `CATEGORY` · `PRODUCT` · `PRODUCT_VARIANT` · `PRODUCT_IMAGE` · `STOCK_RESERVATION`

```mermaid
erDiagram
    CATEGORY        ||--o{ CATEGORY          : "1-N | self-ref parent_id SET NULL"
    CATEGORY        ||--o{ PRODUCT           : "1-N | one category, many products"
    PRODUCT         ||--o{ PRODUCT_VARIANT   : "1-N | one product, many variants CASCADE"
    PRODUCT         ||--o{ PRODUCT_IMAGE     : "1-N | one product, many images CASCADE"
    PRODUCT_VARIANT ||--o{ PRODUCT_IMAGE     : "1-0..1 | variant image SET NULL"
    PRODUCT_VARIANT ||--o{ STOCK_RESERVATION : "1-N | one variant, many reservations"

    CATEGORY {
        uuid id PK "gen_random_uuid()"
        uuid parent_id "self-ref, NULL = root"
        varchar name "NOT NULL"
        varchar slug UK
        text description
        varchar image_url
        integer sort_order "DEFAULT 0"
        boolean is_active "DEFAULT true"
        timestamptz created_at
        timestamptz updated_at
    }

    PRODUCT {
        uuid id PK "gen_random_uuid()"
        uuid category_id "ref CATEGORY.id"
        uuid seller_id "soft-ref SELLERS.id"
        varchar name "5-200 chars"
        varchar slug UK
        text description "rich text / HTML, max 10000"
        jsonb attributes
        varchar status "draft/pending/approved/rejected/active/out_of_stock/inactive"
        text reject_reason
        timestamptz submitted_at
        timestamptz reviewed_at
        uuid reviewed_by "admin user_id"
        integer reject_count "DEFAULT 0"
        timestamptz created_at
        timestamptz updated_at
    }

    PRODUCT_VARIANT {
        uuid id PK "gen_random_uuid()"
        uuid product_id "ref PRODUCT.id CASCADE"
        varchar variant_code UK "3-50 chars alphanumeric+dash"
        varchar variant_name
        jsonb variant_attributes
        decimal price "NOT NULL, > 0"
        decimal original_price
        integer stock_quantity "DEFAULT 0"
        varchar status "active / out_of_stock / inactive"
        integer version "DEFAULT 1, optimistic lock"
        varchar image_url
        timestamptz created_at
        timestamptz updated_at
    }

    PRODUCT_IMAGE {
        uuid id PK "gen_random_uuid()"
        uuid product_id "ref PRODUCT.id CASCADE"
        uuid variant_id "ref PRODUCT_VARIANT.id SET NULL"
        varchar url "MinIO object URL"
        integer sort_order "DEFAULT 0"
        timestamptz created_at
    }

    STOCK_RESERVATION {
        uuid id PK "gen_random_uuid()"
        uuid variant_id "ref PRODUCT_VARIANT.id"
        varchar session_id "checkout session token"
        integer quantity
        varchar status "pending / confirmed / released"
        timestamptz expires_at "NOW() + 15 min"
        timestamptz created_at
        timestamptz updated_at
    }
```

> **Cross-service soft-refs:**
> `PRODUCT_VARIANT.id` ← CART_ITEM.variant_id, ORDER_ITEMS.variant_id
> `STOCK_RESERVATION.session_id` ← PARENT_ORDERS.session_id

---

### 3. Product Service — Cart Domain (PostgreSQL · port 8090)

Tables: `CART` · `CART_ITEM`

> **N-N relationship note:** CART_ITEM is a junction table mediating N-N between CART and PRODUCT_VARIANT.
> A product variant can appear in many carts; a cart contains many variants.
> Constraint `UNIQUE(cart_id, variant_id)` prevents duplicate variant in same cart.

```mermaid
erDiagram
    CART ||--o{ CART_ITEM : "1-N | one cart, many items CASCADE"

    CART {
        uuid id PK "gen_random_uuid()"
        bigint customer_id UK "soft-ref CUSTOMERS.id — 1 cart per customer"
        varchar status "active"
        timestamptz created_at
        timestamptz updated_at
    }

    CART_ITEM {
        uuid id PK "gen_random_uuid()"
        uuid cart_id "ref CART.id CASCADE"
        uuid variant_id "soft-ref PRODUCT_VARIANT.id"
        integer quantity "DEFAULT 1, range 1-1000"
        decimal price_snapshot "price at time of add"
        varchar variant_name_snapshot
        varchar variant_image_snapshot
        timestamptz created_at
        timestamptz updated_at
    }
```

---

### 4. Flash Sale Service (PostgreSQL · port 8085)

Tables: `FS_SESSIONS` · `FS_ITEMS`

```mermaid
erDiagram
    FS_SESSIONS ||--o{ FS_ITEMS : "1-N | one session, many items"

    FS_SESSIONS {
        bigint id PK "BIGSERIAL"
        varchar name "NOT NULL, max 255"
        timestamp start_time "NOT NULL"
        timestamp end_time "NOT NULL, > start_time"
        timestamp registration_deadline "= start_time - 15 min"
        decimal discount_percentage "5,2 — 0 < x <= 100"
        varchar status "UPCOMING / ACTIVE / ENDED"
        timestamp deleted_at "soft delete"
        timestamp created_at
        timestamp updated_at
    }

    FS_ITEMS {
        bigint id PK "BIGSERIAL"
        bigint session_id FK "ref FS_SESSIONS.id"
        uuid product_id "soft-ref PRODUCT.id"
        decimal discount_applied "5,2 — inherited or seller override"
        uuid seller_id "soft-ref SELLERS.id"
        timestamp registered_at
        timestamp created_at
        timestamp updated_at
    }

```

> **Constraint:** UNIQUE(session_id, product_id) on FS_ITEMS prevents duplicate registration.
> **Redis alongside:** `flash_sale:stock:{fs_item_id}` (atomic counter), `flash_sale:triggers` (ZSET).
> **Cross-service:** `FS_ITEMS.id` ← ORDER_ITEMS.fs_item_id (nullable soft-ref).

---

### 5. Order Service (PostgreSQL + Axon · port 8083)

Tables: `PARENT_ORDERS` · `ORDERS` · `ORDER_ITEMS`

```mermaid
erDiagram
    PARENT_ORDERS ||--o{ ORDERS      : "1-N | one parent, many sub-orders"
    ORDERS        ||--o{ ORDER_ITEMS : "1-N | one order, many items CASCADE"

    PARENT_ORDERS {
        bigint id PK "BIGSERIAL"
        bigint customer_id "soft-ref CUSTOMERS.id"
        varchar session_id "soft-ref STOCK_RESERVATION.session_id"
        decimal total_amt "18,2 — sum of sub-orders"
        decimal final_amt "18,2"
        varchar currency "NOT NULL DEFAULT 'VND'"
        varchar status "PENDING_PAYMENT / PAID / CANCELLED"
        timestamp created_at
        timestamp updated_at
    }

    ORDERS {
        bigint id PK "BIGSERIAL"
        bigint parent_order_id FK "ref PARENT_ORDERS.id"
        bigint seller_id "soft-ref SELLERS.id"
        varchar order_code UK "human-readable code"
        bigint customer_id "soft-ref CUSTOMERS.id"
        decimal total_amt "18,2"
        decimal final_amt "18,2"
        decimal net_payout_amount "18,2 — after platform commission"
        varchar status "PENDING/PAID/SHIPPING/DELIVERED/CANCELLED/REFUNDED/PARTIALLY_REFUNDED/RETURNED"
        varchar cancelled_by "BUYER / SELLER / SYSTEM"
        text cancel_reason
        jsonb shipping_address "snapshot at checkout"
        timestamp shipping_deadline
        varchar tracking_number
        varchar carrier
        timestamp paid_at
        timestamp return_window_end "delivered_at + 7 days"
        timestamp shipped_at
        timestamp delivered_at
        timestamp created_at
        timestamp updated_at
    }

    ORDER_ITEMS {
        bigint id PK "BIGSERIAL"
        bigint order_id FK "ref ORDERS.id CASCADE"
        varchar sku_code "snapshot at order time"
        uuid variant_id "soft-ref PRODUCT_VARIANT.id"
        varchar name_snapshot
        varchar image_snapshot
        decimal price_snapshot
        int quantity
        bigint fs_item_id "nullable soft-ref FS_ITEMS.id"
        timestamp created_at
    }
```

> **Cross-service soft-refs:**
> `ORDERS.id` ← SELLER_TRANSFERS.order_id (payment-service), REFUNDS.order_id (refund-service)
> `PARENT_ORDERS.id` ← TRANSACTIONS.parent_order_id (payment-service)

---

### 6. Payment Service (PostgreSQL + Axon · port 8082)

Tables: `SELLER_STRIPE_ACCOUNTS` · `TRANSACTIONS` · `SELLER_TRANSFERS`

```mermaid
erDiagram
    TRANSACTIONS ||--o{ SELLER_TRANSFERS : "1-N | one transaction, many seller transfers"

    SELLER_STRIPE_ACCOUNTS {
        bigint id PK "BIGSERIAL"
        bigint seller_id FK "ref SELLERS.id UNIQUE"
        varchar stripe_account_id "Stripe acct_xxx"
        varchar account_status "PENDING / ACTIVE / SUSPENDED"
        boolean charges_enabled "Stripe KYC — can accept charges"
        boolean payouts_enabled "can receive payouts"
        boolean details_submitted "KYC completed"
        text onboarding_url "Express onboarding link"
        text express_dashboard_url
        timestamp onboarding_url_expires_at "24h TTL"
        timestamp created_at
        timestamp updated_at
    }

    TRANSACTIONS {
        bigint id PK "BIGSERIAL"
        bigint parent_order_id FK "ref PARENT_ORDERS.id UNIQUE"
        decimal amount "18,2"
        varchar trans_ref "Stripe PaymentIntent ID pi_xxx"
        varchar stripe_transfer_id "Stripe transfer tr_xxx"
        decimal application_fee_amount "platform commission"
        varchar stripe_connect_mode "destination_charge"
        varchar status "PENDING / SUCCESS / FAILED / CANCELLED"
        jsonb raw_response "Stripe API response snapshot"
        timestamp pay_at
        timestamp created_at
        timestamp updated_at
    }

    SELLER_TRANSFERS {
        bigint id PK "BIGSERIAL"
        bigint order_id FK "ref ORDERS.id UNIQUE"
        bigint seller_id "soft-ref SELLERS.id"
        bigint transaction_id FK "ref TRANSACTIONS.id"
        decimal transfer_amount "18,2"
        decimal refunded_amount "18,2 DEFAULT 0"
        decimal net_payout_amount "transfer_amount - commission"
        decimal platform_commission_amt "18,2"
        varchar stripe_transfer_id "Stripe tr_xxx"
        varchar stripe_payout_id "Stripe payout po_xxx"
        varchar status "ELIGIBLE / IN_TRANSIT / PAID / FAILED / RETRYING"
        timestamp delivered_at
        timestamp payout_eligible_at "delivered_at + 7 days"
        timestamp payout_at
        int payout_retry_count "DEFAULT 0"
        varchar failure_code "Stripe error code, nullable"
        text failure_reason "payout failure description, nullable"
        timestamp created_at
        timestamp updated_at
    }
```

---

### 6.5 Refund Service (PostgreSQL · port 8094)

Tables: `REFUNDS` · `REFUND_ITEMS`

> **N-N relationship note:** REFUND_ITEMS is a junction table mediating N-N between REFUNDS and ORDER_ITEMS.
> A refund can cover multiple order items; an order item may be included in multiple partial refunds.

```mermaid
erDiagram
    REFUNDS ||--o{ REFUND_ITEMS : "1-N | one refund, many refund items CASCADE"

    REFUNDS {
        bigint id PK "BIGSERIAL"
        bigint transaction_id FK "ref TRANSACTIONS.id"
        bigint order_id "soft-ref ORDERS.id"
        uuid group_ref "batch refund grouping UUID"
        varchar type "FULL / PARTIAL"
        decimal amount "18,2"
        varchar status "PENDING / SUCCESS / FAILED / REJECTED"
        varchar reason "buyer-provided reason"
        varchar initiated_by "BUYER / SELLER / SYSTEM"
        bigint reviewed_by "admin user_id"
        timestamp reviewed_at
        varchar admin_note
        varchar reject_reason
        varchar refund_ref "Stripe refund re_xxx"
        jsonb raw_response
        timestamp created_at
        timestamp updated_at
    }

    REFUND_ITEMS {
        bigint id PK "BIGSERIAL"
        bigint refund_id FK "ref REFUNDS.id CASCADE"
        bigint item_id "soft-ref ORDER_ITEMS.id"
        decimal refund_amount "18,2"
        varchar reason
        varchar status "PENDING / SUCCESS / REJECTED"
        jsonb evidence_images "array of MinIO URLs"
        varchar reject_reason
        timestamp reviewed_at
        varchar return_tracking_number
        varchar carrier
        timestamp returned_at
    }
```

> **Relationship note:** SELLER_STRIPE_ACCOUNTS is 1-0..1 with SELLERS (cross-service soft-ref).
> One seller has zero or one Stripe account; each Stripe account belongs to exactly one seller.

---

### 7. Notification Service (MongoDB · port 8092)

Collection: `MG_NOTIFICATIONS` (TTL index: 90 days)

```mermaid
erDiagram
    MG_NOTIFICATIONS {
        string id PK "MongoDB ObjectId"
        bigint user_id "soft-ref USERS.id"
        varchar title
        text body
        varchar type "order.shipped / payment.success / refund.approved / flash_sale.started / ..."
        jsonb metadata "type-specific payload (order_id, tracking_number, etc.)"
        boolean is_read "DEFAULT false"
        timestamp read_at "NULLABLE — when marked as read"
        timestamp created_at "TTL index: auto-delete after 90 days"
    }
```

> **Redis Pub/Sub:** Channel `user:{userId}` used for real-time SSE push — messages NOT persisted in Redis.
> **TTL:** `db.notifications.createIndex({ created_at: 1 }, { expireAfterSeconds: 7776000 })`

---

### 8. AI Chat Service (MongoDB · port 8093)

Collections: `CHAT_SESSIONS` · `CHAT_MESSAGES` · `PENDING_CONFIRMATIONS` · `TOOL_CALL_LOGS`

```mermaid
erDiagram
    CHAT_SESSIONS     ||--o{ CHAT_MESSAGES         : "1-N | one session, many messages"
    CHAT_SESSIONS     ||--o{ PENDING_CONFIRMATIONS : "1-N | one session, many confirmations"
    CHAT_SESSIONS     ||--o{ TOOL_CALL_LOGS        : "1-N | one session, many tool logs"
    CHAT_MESSAGES     ||--o{ TOOL_CALL_LOGS        : "1-0..1 | message tool call log"

    CHAT_SESSIONS {
        uuid id PK "MongoDB UUID"
        bigint user_id "soft-ref USERS.id"
        varchar status "ACTIVE / CLOSED / EXPIRED"
        text context_summary "LLM-generated summary (triggered after 50+ messages)"
        timestamptz created_at
        timestamptz updated_at
        timestamptz closed_at
    }

    CHAT_MESSAGES {
        uuid id PK
        uuid session_id "ref CHAT_SESSIONS.id"
        varchar role "USER / ASSISTANT / TOOL_CALL / TOOL_RESULT"
        text content "message text or tool JSON payload"
        varchar tool_name "only for TOOL_CALL and TOOL_RESULT"
        int sequence_no "UNIQUE(session_id, sequence_no)"
        int tokens_used "ASSISTANT messages only"
        timestamptz created_at
    }

    PENDING_CONFIRMATIONS {
        uuid id PK "confirmation token"
        uuid session_id "ref CHAT_SESSIONS.id"
        bigint user_id "soft-ref USERS.id"
        varchar tool_name "Level-3 action being confirmed"
        jsonb tool_arguments "tool call payload"
        varchar summary "human-readable description for UI display"
        varchar status "PENDING / CONFIRMED / REJECTED / EXPIRED"
        timestamptz expires_at "NOW() + 5 min — TTL index"
        timestamptz confirmed_at "NULLABLE — when confirmed/rejected"
        timestamptz created_at
        timestamptz updated_at
    }

    TOOL_CALL_LOGS {
        bigint id PK "BIGSERIAL"
        uuid session_id "ref CHAT_SESSIONS.id"
        uuid message_id "ref CHAT_MESSAGES.id"
        bigint user_id "soft-ref USERS.id"
        varchar tool_name
        jsonb arguments "tool call input"
        jsonb result "tool call output"
        varchar status "SUCCESS / ERROR / TIMEOUT"
        int latency_ms
        varchar error_code
        text error_message
        timestamptz created_at
    }
```

> **TTL on PENDING_CONFIRMATIONS:** `{ expires_at: 1 }, { expireAfterSeconds: 0 }`

---

### 9. Infrastructure Tables (PostgreSQL · shared)

Tables: `SHEDLOCK` · `OUTBOX_EVENTS` · `FAILED_EVENTS`

> OUTBOX_EVENTS và FAILED_EVENTS tạo sẵn trong schema nhưng **chưa active trong MVP**.

```mermaid
erDiagram
    SHEDLOCK {
        varchar name PK "distributed lock key = job name"
        timestamp lock_until "auto-release timestamp"
        timestamp locked_at
        varchar locked_by "hostname:instanceId"
    }

    OUTBOX_EVENTS {
        bigint id PK "BIGSERIAL"
        varchar topic "Kafka topic name"
        jsonb payload "event envelope with event_id, event_type, data"
        varchar status "PENDING / PROCESSED / FAILED"
        int retry_count "DEFAULT 0"
        timestamp processed_at
        timestamp created_at
    }

    FAILED_EVENTS {
        bigint id PK "BIGSERIAL"
        varchar topic_or_task "source topic or scheduled job name"
        jsonb payload "original event payload"
        text error_reason
        int retry_count "DEFAULT 0"
        varchar status "PENDING / DEAD / RESOLVED / MANUAL_INTERVENTION"
        timestamp created_at
        timestamp updated_at
    }
```

---

## PHẦN II: COMPACT ERD — CHỈ KHÓA CHÍNH & KHÓA NGOẠI

> Mỗi entity chỉ liệt kê: cột `PK` và các cột `FK`. Soft-references (cross-service, không có hard DB constraint) được đánh dấu `"soft-ref"`.

```mermaid
erDiagram

    %% ═══════════════════════════════════════════════
    %% IDENTITY SERVICE
    %% ═══════════════════════════════════════════════
    USERS          ||--o{ ROLES        : "1-N | user_id FK"
    USERS          ||--o| CUSTOMERS    : "1-0..1 | user_id FK UNIQUE"
    USERS          ||--o| SELLERS      : "1-0..1 | user_id FK UNIQUE"
    USERS          ||--o| ADMINS       : "1-0..1 | user_id FK UNIQUE"
    USERS          ||--o{ ADDRESSES    : "1-N | user_id FK"

    USERS     { bigint id PK }
    ROLES     { bigint id PK
                bigint user_id FK }
    CUSTOMERS { bigint id PK
                bigint user_id FK }
    SELLERS   { bigint id PK
                bigint user_id FK }
    ADMINS    { bigint id PK
                bigint user_id FK }
    ADDRESSES { bigint id PK
                bigint user_id FK }

    %% ═══════════════════════════════════════════════
    %% CATALOG DOMAIN
    %% ═══════════════════════════════════════════════
    CATEGORY        ||--o{ CATEGORY          : "1-N | parent_id self-ref"
    CATEGORY        ||--o{ PRODUCT           : "1-N | category_id FK"
    PRODUCT         ||--o{ PRODUCT_VARIANT   : "1-N | product_id FK"
    PRODUCT         ||--o{ PRODUCT_IMAGE     : "1-N | product_id FK"
    PRODUCT_VARIANT ||--o{ PRODUCT_IMAGE     : "1-0..1 | variant_id FK"
    PRODUCT_VARIANT ||--o{ STOCK_RESERVATION : "1-N | variant_id FK"

    CATEGORY          { uuid id PK
                        uuid parent_id FK }
    PRODUCT           { uuid id PK
                        uuid category_id FK
                        uuid seller_id "soft-ref" }
    PRODUCT_VARIANT   { uuid id PK
                        uuid product_id FK }
    PRODUCT_IMAGE     { uuid id PK
                        uuid product_id FK
                        uuid variant_id FK }
    STOCK_RESERVATION { uuid id PK
                        uuid variant_id FK }

    %% ═══════════════════════════════════════════════
    %% CART DOMAIN
    %% ═══════════════════════════════════════════════
    CART ||--o{ CART_ITEM : "1-N | cart_id FK"

    CART      { uuid id PK
                bigint customer_id "soft-ref" }
    CART_ITEM { uuid id PK
                uuid cart_id FK
                uuid variant_id "soft-ref" }

    %% ═══════════════════════════════════════════════
    %% FLASH SALE SERVICE
    %% ═══════════════════════════════════════════════
    FS_SESSIONS ||--o{ FS_ITEMS : "1-N | session_id FK"

    FS_SESSIONS { bigint id PK }
    FS_ITEMS    { bigint id PK
                  bigint session_id FK
                  uuid product_id "soft-ref"
                  uuid seller_id "soft-ref" }

    %% ═══════════════════════════════════════════════
    %% ORDER SERVICE
    %% ═══════════════════════════════════════════════
    PARENT_ORDERS ||--o{ ORDERS      : "1-N | parent_order_id FK"
    ORDERS        ||--o{ ORDER_ITEMS : "1-N | order_id FK"

    PARENT_ORDERS { bigint id PK
                    bigint customer_id "soft-ref"
                    varchar session_id "soft-ref" }
    ORDERS        { bigint id PK
                    bigint parent_order_id FK
                    bigint seller_id "soft-ref"
                    bigint customer_id "soft-ref" }
    ORDER_ITEMS   { bigint id PK
                    bigint order_id FK
                    uuid variant_id "soft-ref"
                    bigint fs_item_id "soft-ref nullable" }

    %% ═══════════════════════════════════════════════
    %% PAYMENT SERVICE
    %% ═══════════════════════════════════════════════
    SELLERS          ||--o| SELLER_STRIPE_ACCOUNTS : "1-0..1 | seller_id FK UNIQUE"
    TRANSACTIONS     ||--o{ SELLER_TRANSFERS       : "1-N | transaction_id FK"
    TRANSACTIONS     ||--o{ REFUNDS                : "1-N | transaction_id FK"
    REFUNDS          ||--o{ REFUND_ITEMS           : "1-N | refund_id FK"

    SELLER_STRIPE_ACCOUNTS { bigint id PK
                              bigint seller_id FK }
    TRANSACTIONS           { bigint id PK
                              bigint parent_order_id FK }
    SELLER_TRANSFERS       { bigint id PK
                              bigint transaction_id FK
                              bigint order_id FK
                              bigint seller_id "soft-ref" }
    REFUNDS                { bigint id PK
                              bigint transaction_id FK
                              bigint order_id "soft-ref" }
    REFUND_ITEMS           { bigint id PK
                              bigint refund_id FK
                              bigint item_id "soft-ref" }

    %% ═══════════════════════════════════════════════
    %% NOTIFICATION SERVICE (MongoDB)
    %% ═══════════════════════════════════════════════
    USERS ||--o{ MG_NOTIFICATIONS : "1-N | user_id soft-ref"

    MG_NOTIFICATIONS { string id PK
                        bigint user_id "soft-ref" }

    %% ═══════════════════════════════════════════════
    %% AI CHAT SERVICE (MongoDB)
    %% ═══════════════════════════════════════════════
    USERS             ||--o{ CHAT_SESSIONS        : "1-N | user_id soft-ref"
    CHAT_SESSIONS     ||--o{ CHAT_MESSAGES         : "1-N | session_id FK"
    CHAT_SESSIONS     ||--o{ PENDING_CONFIRMATIONS : "1-N | session_id FK"
    CHAT_SESSIONS     ||--o{ TOOL_CALL_LOGS        : "1-N | session_id FK"
    CHAT_MESSAGES     ||--o{ TOOL_CALL_LOGS        : "1-0..1 | message_id FK"

    CHAT_SESSIONS         { uuid id PK
                             bigint user_id "soft-ref" }
    CHAT_MESSAGES         { uuid id PK
                             uuid session_id FK }
    PENDING_CONFIRMATIONS { uuid id PK
                             uuid session_id FK
                             bigint user_id "soft-ref" }
    TOOL_CALL_LOGS        { bigint id PK
                             uuid session_id FK
                             uuid message_id FK
                             bigint user_id "soft-ref" }

    %% ═══════════════════════════════════════════════
    %% INFRASTRUCTURE
    %% ═══════════════════════════════════════════════
    SHEDLOCK      { varchar name PK }
    OUTBOX_EVENTS { bigint id PK }
    FAILED_EVENTS { bigint id PK }
```

---

## PHẦN III: CROSS-SERVICE REFERENCE MAP

Soft references không có hard FK constraint — enforced ở application layer và qua Kafka eventual consistency:

| From (Service) | Column | References (Service) | Relationship |
|----------------|--------|----------------------|--------------|
| PRODUCT (product) | seller_id | Identity · SELLERS.id | N-1 |
| PRODUCT (product) | category_id | Catalog · CATEGORY.id | N-1 (same service, hard FK) |
| CART (product) | customer_id | Identity · CUSTOMERS.id | 1-1 |
| CART_ITEM (product) | variant_id | Catalog · PRODUCT_VARIANT.id | N-1 (same service, hard FK) |
| FS_ITEMS (flashsale) | product_id | Catalog · PRODUCT.id | N-1 |
| FS_ITEMS (flashsale) | seller_id | Identity · SELLERS.id | N-1 |
| PARENT_ORDERS (order) | customer_id | Identity · CUSTOMERS.id | N-1 |
| PARENT_ORDERS (order) | session_id | Catalog · STOCK_RESERVATION.session_id | 1-1 |
| ORDERS (order) | seller_id | Identity · SELLERS.id | N-1 |
| ORDERS (order) | customer_id | Identity · CUSTOMERS.id | N-1 |
| ORDER_ITEMS (order) | variant_id | Catalog · PRODUCT_VARIANT.id | N-1 |
| ORDER_ITEMS (order) | fs_item_id | FlashSale · FS_ITEMS.id | N-0..1 (nullable) |
| TRANSACTIONS (payment) | parent_order_id | Order · PARENT_ORDERS.id | 1-1 |
| SELLER_TRANSFERS (payment) | order_id | Order · ORDERS.id | 1-1 |
| SELLER_TRANSFERS (payment) | seller_id | Identity · SELLERS.id | N-1 |
| REFUNDS (refund) | transaction_id | Payment · TRANSACTIONS.id | N-1 |
| REFUNDS (refund) | order_id | Order · ORDERS.id | N-1 |
| REFUND_ITEMS (refund) | item_id | Order · ORDER_ITEMS.id | N-1 |
| MG_NOTIFICATIONS (notif) | user_id | Identity · USERS.id | N-1 |
| CHAT_SESSIONS (ai-chat) | user_id | Identity · USERS.id | N-1 |
| PENDING_CONFIRMATIONS (ai-chat) | user_id | Identity · USERS.id | N-1 |
| TOOL_CALL_LOGS (ai-chat) | user_id | Identity · USERS.id | N-1 |

---

## PHẦN IV: ELASTICSEARCH INDEX — skus

Không có ERD (document store không có schema cố định), nhưng mapping field được liệt kê dưới đây:

```
Index: skus  (SKU-first — 1 document per SKU; collapse by product_id khi search)

Identifier fields:
  sku_id              keyword   PK
  product_id          keyword   group key — field collapse khi search
  seller_id           keyword

Product fields:
  product_name        text      ← Vietnamese analyzer (icu + asciifolding + synonym)
  product_slug        keyword
  product_description text      ← Vietnamese analyzer
  category_id         keyword
  category_path       keyword[]

Variant fields:
  variant_name        keyword
  variant_attributes  object (dynamic mapping)
  sku_code            keyword

Pricing fields:
  price               double
  original_price      double
  has_discount        boolean
  discount_pct        integer
  flash_session_id    keyword   — null if no active flash sale
  flash_price         double    — null if no active flash sale

Availability fields:
  stock_status        keyword   — in_stock / out_of_stock
  product_status      keyword
  sku_status          keyword
  is_active           boolean

Display fields (not indexed):
  thumbnail_url       keyword   (index: false)
  sku_image_url       keyword   (index: false)
  seller_name         text

Timestamp fields:
  product_created_at  date
  sku_updated_at      date
```

---

*Nguồn: `documents/data-models/ERD_FULL_SYSTEM.md` · `documents/database-entities.md`*
