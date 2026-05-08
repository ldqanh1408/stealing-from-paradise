# Entity Relationship Diagram — Compact

> Sơ đồ quan hệ các bảng tổng quát, rút gọn theo từng service domain.
> `<>` = MongoDB ObjectId, `[]` = Elasticsearch, `{}` = Redis

---

## 1. Identity Domain

```mermaid
erDiagram
    USERS ||--o{ CUSTOMERS : "1:1"
    USERS ||--o{ SELLERS : "1:1"
    USERS ||--o{ ADMINS : "1:1"
    USERS ||--o{ ADDRESSES : "1:N"

    USERS {
        BIGSERIAL id PK
        VARCHAR username "UNIQUE"
        VARCHAR email "UNIQUE"
        VARCHAR phone "UNIQUE"
        VARCHAR password "Bcrypt"
        VARCHAR full_name
        VARCHAR status "ACTIVE | LOCKED"
        VARCHAR role "BUYER | SELLER | ADMIN, default BUYER"
        INT version "Optimistic Lock, default 0"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    CUSTOMERS {
        BIGSERIAL id PK
        BIGINT user_id FK "UNIQUE"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    SELLERS {
        BIGSERIAL id PK
        BIGINT user_id FK "UNIQUE"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    ADMINS {
        BIGSERIAL id PK
        BIGINT user_id FK "UNIQUE"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    ADDRESSES {
        BIGSERIAL id PK
        BIGINT user_id FK
        INT province_id
        INT district_id
        TEXT full_address
        BOOLEAN is_default
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
```

---

## 2. Catalog Domain — Product Service

```mermaid
erDiagram
    MG_CATEGORIES ||--o{ MG_CATEGORIES : "self-ref (parent)"
    MG_CATEGORIES ||--o{ MG_PRODUCTS : "1:N"
    SELLERS ||--o{ MG_PRODUCTS : "seller (1:N)"
    MG_PRODUCTS ||--o{ MG_PRODUCT_VARIANTS : "1:N"
    MG_PRODUCTS ||--o{ MG_INVENTORIES : "1:N"
    MG_PRODUCT_VARIANTS ||--|| MG_INVENTORIES : "1:1 (sku)"
    MG_CARTS ||--o{ MG_CART_ITEMS : "1:N"
    CUSTOMERS ||--|| MG_CARTS : "1:1"
    FS_ITEMS |o--o{ MG_CART_ITEMS : "nullable"

    MG_CATEGORIES {
        ObjectId id PK
        string name
        string slug "UNIQUE"
        ObjectId parent_id "FK self-ref"
        int level
    }

    MG_PRODUCTS {
        ObjectId id PK
        long seller_id FK "SELLERS.id"
        ObjectId category_id FK "MG_CATEGORIES.id"
        string name
        string status "PENDING | APPROVED | REJECTED"
        boolean is_flash
        datetime deleted_at "Soft delete"
    }

    MG_PRODUCT_VARIANTS {
        ObjectId id PK
        ObjectId product_id FK "MG_PRODUCTS.id"
        UUID image_id FK "IMAGES.id"
        string sku_code "UNIQUE"
        string tier_name
        decimal price
    }

    MG_INVENTORIES {
        ObjectId id PK
        string sku_code "UNIQUE, FK → MG_PRODUCT_VARIANTS.sku_code"
        ObjectId product_id FK "MG_PRODUCTS.id"
        int stock_total
        int stock_locked
        int stock_available
        int stock_flash_reserved
    }

    MG_CARTS {
        ObjectId id PK
        long customer_id FK "UNIQUE, CUSTOMERS.id"
        int total_items
    }

    MG_CART_ITEMS {
        ObjectId id PK
        ObjectId cart_id FK "MG_CARTS.id"
        ObjectId variant_id FK "MG_PRODUCT_VARIANTS.id"
        string sku_code
        bigint fs_item_id FK "FS_ITEMS.id"
        decimal price_snapshot
        boolean is_selected "default: true"
        int quantity
        timestamp added_at
    }
```

---

## 3. Order Domain — Order Service

```mermaid
erDiagram
    CUSTOMERS ||--o{ PARENT_ORDERS : "buyer (1:N)"
    PARENT_ORDERS ||--o{ ORDERS : "1:N"
    SELLERS ||--o{ ORDERS : "seller (1:N)"
    ORDERS ||--o{ ORDER_ITEMS : "1:N"

    PARENT_ORDERS {
        BIGSERIAL id PK
        BIGINT customer_id FK "CUSTOMERS.id"
        DECIMAL total_amt
        DECIMAL final_amt
        VARCHAR status "PENDING_PAYMENT | PAID | CANCELLED"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    ORDERS {
        BIGSERIAL id PK
        BIGINT parent_order_id FK "PARENT_ORDERS.id"
        BIGINT seller_id FK "SELLERS.id"
        BIGINT customer_id FK "CUSTOMERS.id"
        VARCHAR order_code "UNIQUE"
        DECIMAL total_amt
        DECIMAL final_amt
        VARCHAR status "PENDING | PAID | SHIPPING | DELIVERED | RETURNED | REFUNDED | PARTIALLY_REFUNDED | CANCELLED"
        VARCHAR cancelled_by "BUYER | SELLER | SYSTEM"
        TEXT cancel_reason
        JSONB shipping_address
        TIMESTAMP shipping_deadline "created_at + 3 days"
        TIMESTAMP paid_at
        TIMESTAMP shipped_at
        TIMESTAMP delivered_at
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    ORDER_ITEMS {
        BIGSERIAL id PK
        BIGINT order_id FK "ORDERS.id"
        VARCHAR sku_code
        ObjectId variant_id FK "MG_PRODUCT_VARIANTS.id"
        VARCHAR name_snapshot
        VARCHAR image_snapshot
        DECIMAL price_snapshot
        INT quantity
        bigint fs_item_id FK "FS_ITEMS.id"
        VARCHAR tracking_number "Mã vận đơn cho món"
        VARCHAR carrier "GHTK, Viettel Post, ..."
        TIMESTAMP created_at
    }
```

---

## 4. Payment Domain — Payment Service

```mermaid
erDiagram
    SELLERS ||--|| SELLER_STRIPE_ACCOUNTS : "1:0..1"
    PARENT_ORDERS ||--o{ TRANSACTIONS : "1:N"
    TRANSACTIONS ||--o{ SELLER_TRANSFERS : "1:N"
    TRANSACTIONS ||--o{ REFUNDS : "1:N"
    ORDERS ||--o{ REFUNDS : "1:N"
    ORDERS ||--o{ SELLER_TRANSFERS : "1:N"
    REFUNDS ||--o{ REFUND_ITEMS : "1:N"
    ORDER_ITEMS ||--o{ REFUND_ITEMS : "1:N"

    SELLER_STRIPE_ACCOUNTS {
        BIGSERIAL id PK
        BIGINT seller_id FK "UNIQUE"
        VARCHAR stripe_account_id "acct_xxx"
        VARCHAR account_status "PENDING | ACTIVE | RESTRICTED | SUSPENDED"
        BOOLEAN charges_enabled
        BOOLEAN payouts_enabled
        BOOLEAN details_submitted
        TEXT onboarding_url "expires 24h"
        TEXT express_dashboard_url
        TIMESTAMP onboarding_url_expires_at
    }

    TRANSACTIONS {
        BIGSERIAL id PK
        BIGINT parent_order_id FK "PARENT_ORDERS.id"
        DECIMAL amount
        VARCHAR trans_ref "PaymentIntent ID (pi_xxx)"
        VARCHAR stripe_transfer_id "Transfer ID tr_xxx"
        DECIMAL application_fee_amount
        VARCHAR stripe_connect_mode "DESTINATION | TRANSFER | NONE"
        VARCHAR status "SUCCESS | FAILED | REFUNDED | PARTIALLY_REFUNDED"
        JSONB raw_response
        TIMESTAMP pay_at
    }

    SELLER_TRANSFERS {
        BIGSERIAL id PK
        BIGINT order_id FK "ORDERS.id"
        BIGINT seller_id FK "SELLERS.id"
        BIGINT transaction_id FK "TRANSACTIONS.id"
        DECIMAL transfer_amount "gross"
        VARCHAR stripe_transfer_id "for Reversal"
        TIMESTAMP delivered_at
        TIMESTAMP payout_eligible_at "delivered + 7 days"
        DECIMAL platform_commission_amt "5% of transfer_amount"
        TIMESTAMP payout_at
        INTEGER payout_retry_count
        VARCHAR status "PENDING | AWAITING_DELIVERY | RETURN_WINDOW | READY_FOR_PAYOUT | PAID_OUT | FAILED | SKIPPED | REFUNDED | REVERSED | PARTIALLY_REVERSED"
    }

    REFUNDS {
        BIGSERIAL id PK
        BIGINT transaction_id FK "TRANSACTIONS.id"
        BIGINT order_id FK "ORDERS.id"
        UUID group_ref
        VARCHAR type "FULL | PARTIAL"
        VARCHAR initiated_by "BUYER | SELLER | SYSTEM"
        VARCHAR refund_reason_type "BUYER_REQUEST | RETURN_TO_SENDER | ADMIN_OVERRIDE"
        DECIMAL amount
        VARCHAR reason
        VARCHAR status "PENDING | SUCCESS | FAILED | REJECTED"
        JSONB evidence_images "MinIO URLs"
        VARCHAR reject_reason
        TEXT admin_note
        BIGINT reviewed_by FK "ADMINS.id"
        TIMESTAMP reviewed_at
        VARCHAR refund_ref "re_xxx"
        JSONB raw_response
    }

    REFUND_ITEMS {
        BIGSERIAL id PK
        BIGINT refund_id FK "REFUNDS.id"
        BIGINT item_id FK "ORDER_ITEMS.id"
        INT quantity
        DECIMAL refund_amount
        VARCHAR item_reason
        VARCHAR status "PENDING | SUCCESS | FAILED"
        VARCHAR return_tracking_number "Mã vận đơn hoàn"
        VARCHAR carrier "Đơn vị vận chuyển trả hàng"
        JSONB return_evidence_images "MinIO URLs"
        TIMESTAMP returned_at "Seller xác nhận nhận lại"
    }
```

---

## 5. Flash Sale Domain

```mermaid
erDiagram
    FS_SESSIONS ||--o{ FS_ITEMS : "1:N"
    FS_SESSIONS ||--o{ FS_REMINDERS : "1:N"
    CUSTOMERS ||--o{ FS_REMINDERS : "1:N"
    MG_PRODUCT_VARIANTS ||--o{ FS_ITEMS : "1:N (sku)"

    FS_SESSIONS {
        BIGSERIAL id PK
        VARCHAR name
        TIMESTAMP start_time
        TIMESTAMP end_time
        VARCHAR status "UPCOMING | ACTIVE | ENDED"
        TIMESTAMP deleted_at "Soft delete"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    FS_ITEMS {
        BIGSERIAL id PK
        BIGINT session_id FK "FS_SESSIONS.id"
        VARCHAR sku_code FK "MG_PRODUCT_VARIANTS.sku_code"
        DECIMAL flash_price
        INT flash_stock
        INT limit_per_user
        INT sold_qty
        VARCHAR status "PENDING | APPROVED | REJECTED | CANCELLED"
        INT version "Optimistic Lock"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    FS_REMINDERS {
        BIGSERIAL id PK
        BIGINT customer_id FK "CUSTOMERS.id"
        BIGINT session_id FK "FS_SESSIONS.id"
        TIMESTAMP created_at
    }
```

---

## 6. AI Chat Domain

```mermaid
erDiagram
    CHAT_SESSIONS ||--o{ CHAT_MESSAGES : "1:N"
    CHAT_SESSIONS ||--o{ PENDING_CONFIRMATIONS : "1:N"
    CHAT_SESSIONS ||--o{ TOOL_CALL_LOGS : "1:N"
    CHAT_SESSIONS ||--o{ OUTBOX_EVENTS_AI : "1:N"
    CHAT_MESSAGES ||--o{ PENDING_CONFIRMATIONS : "1:N"
    CHAT_MESSAGES ||--o{ TOOL_CALL_LOGS : "1:N"

    CHAT_SESSIONS {
        UUID id PK "gen_random_uuid()"
        BIGINT user_id FK "USERS.id"
        session_status status "ACTIVE | CLOSED | EXPIRED"
        TEXT context_summary
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
        TIMESTAMPTZ closed_at
    }

    CHAT_MESSAGES {
        UUID id PK "gen_random_uuid()"
        UUID session_id FK "CHAT_SESSIONS.id"
        message_role role "USER | ASSISTANT | TOOL_CALL | TOOL_RESULT"
        TEXT content "JSON string for TOOL_CALL/TOOL_RESULT"
        VARCHAR(100) tool_name "TOOL_CALL/TOOL_RESULT only"
        INT sequence_no "UNIQUE (session_id, sequence_no)"
        INT tokens_used "ASSISTANT only"
        TIMESTAMPTZ created_at
    }

    PENDING_CONFIRMATIONS {
        UUID id PK "confirm token"
        UUID session_id FK "CHAT_SESSIONS.id"
        UUID message_id FK "CHAT_MESSAGES.id"
        BIGINT user_id FK "USERS.id"
        confirm_action action_type "CANCEL_ORDER | UPDATE_PROFILE | DELETE_ACCOUNT | CUSTOM"
        JSONB payload
        confirm_status status "PENDING | CONFIRMED | REJECTED | EXPIRED"
        TIMESTAMPTZ expires_at "now + 5 min"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ resolved_at
    }

    TOOL_CALL_LOGS {
        UUID id PK "gen_random_uuid()"
        UUID session_id FK "CHAT_SESSIONS.id"
        UUID message_id FK "CHAT_MESSAGES.id"
        BIGINT user_id FK "USERS.id"
        VARCHAR(100) tool_name
        JSONB input_params
        JSONB output
        tool_call_status status "SUCCESS | FAILED | BLOCKED | TIMEOUT"
        INT duration_ms
        SMALLINT risk_level "1 | 2 | 3"
        TIMESTAMPTZ created_at
    }

    OUTBOX_EVENTS_AI {
        UUID id PK "gen_random_uuid()"
        VARCHAR(100) event_type
        JSONB payload
        outbox_status status "PENDING | PROCESSING | DONE | FAILED"
        SMALLINT retry_count "default 0"
        TEXT error_message
        TIMESTAMPTZ created_at
        TIMESTAMPTZ processed_at
    }
```

---

## 7. Media Bridge (PostgreSQL)

```mermaid
erDiagram
    IMAGES ||--o{ MG_PRODUCT_IMAGES : "1:N"
    MG_PRODUCTS ||--o{ MG_PRODUCT_IMAGES : "1:N"

    IMAGES {
        UUID id PK "gen_random_uuid()"
        VARCHAR bucket "MinIO bucket"
        VARCHAR object_key "MinIO object key"
        VARCHAR content_type
        BIGINT file_size "bytes"
        TEXT url "Public URL (pre-signed or CDN)"
        BIGINT uploaded_by FK "USERS.id"
        TIMESTAMP created_at
    }

    MG_PRODUCT_IMAGES {
        BIGSERIAL id PK
        VARCHAR product_id FK "MG_PRODUCTS.id"
        UUID image_id FK "IMAGES.id"
        BOOLEAN is_main "default: false"
        INT sort_order "default: 0"
    }

    USERS ||--o{ MG_NOTIFICATIONS : "1:N"

    MG_NOTIFICATIONS {
        ObjectId id PK "Mongo ObjectId"
        BIGINT user_id FK "USERS.id"
        VARCHAR title
        TEXT body
        VARCHAR type
        JSONB metadata
        BOOL is_read
        TIMESTAMP created_at "TTL 90 days"
    }
```

---

## 8. Infrastructure Tables

```mermaid
erDiagram
    OUTBOX_EVENTS ||--o| FAILED_EVENTS : "escalates"

    OUTBOX_EVENTS {
        BIGSERIAL id PK
        VARCHAR topic
        JSONB payload
        VARCHAR status "PENDING | PROCESSED | FAILED"
        INT retry_count
        TIMESTAMP processed_at
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    FAILED_EVENTS {
        BIGSERIAL id PK
        VARCHAR topic_or_task
        JSONB payload
        TEXT error_reason
        INT retry_count
        VARCHAR status "PENDING | DEAD | RESOLVED | MANUAL_INTERVENTION"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    SHEDLOCK {
        VARCHAR name PK
        TIMESTAMP lock_until
        TIMESTAMP locked_at
        VARCHAR locked_by
    }
```

---

## 9. Search Index

```mermaid
erDiagram
    ES_PRODUCTS_INDEX {
        keyword id "Mongo ObjectId"
        text name
        text description
        long seller_id
        text seller_name
        keyword category_id
        keyword category_name
        double price_min
        double price_max
        integer stock_available
        boolean is_flash
        keyword status
        nested attributes
        keyword[] tags
        date created_at
        date updated_at
    }
```

---

## 10. Redis Cache Keys

```mermaid
flowchart LR
    subgraph "Redis — AI Chat Service"
        RC1["rate:{userId}
        TTL: 60s
        Rate limit counter (20 req/phút)"]
        RC2["tool:rate:{userId}
        TTL: 60s
        Tool call rate limit (10/phút)"]
        RC3["ctx:{sessionId}
        TTL: 30 min
        Session message cache (20 messages)"]
        RC4["pending:{confirmId}
        TTL: 5 min
        Confirm fast-lookup"]
        RC5["buf:{sessionId}
        TTL: 10 min
        Product search buffer (20 SP)"]
        RC6["tool:cache:{hash}
        TTL: 60s
        Tool result cache (Mức 1)"]
    end
```
