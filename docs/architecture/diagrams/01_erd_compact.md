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
        INT version "Optimistic Lock"
        TIMESTAMP created_at
    }

    CUSTOMERS {
        BIGSERIAL id PK
        BIGINT user_id FK "UNIQUE"
    }

    SELLERS {
        BIGSERIAL id PK
        BIGINT user_id FK "UNIQUE"
        BOOLEAN product_posting_suspended
    }

    ADMINS {
        BIGSERIAL id PK
        BIGINT user_id FK "UNIQUE"
    }

```

---

## 2. Catalog Domain — Product Service

```mermaid
erDiagram
    MG_CATEGORIES ||--o{ MG_CATEGORIES : "self-ref (parent)"
    MG_CATEGORIES ||--o{ MG_PRODUCTS : "1:N"
    USERS ||--o{ MG_PRODUCTS : "seller (1:N)"
    MG_PRODUCTS ||--o{ MG_PRODUCT_VARIANTS : "1:N"
    MG_PRODUCTS ||--o{ MG_INVENTORIES : "1:N"
    MG_PRODUCT_VARIANTS ||--|| MG_INVENTORIES : "1:1 (sku)"
    MG_CARTS ||--o{ MG_CART_ITEMS : "1:N"
    USERS ||--|| MG_CARTS : "1:1"
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
        long seller_id FK
        ObjectId category_id FK
        string name
        string status "PENDING | APPROVED | REJECTED"
        datetime deleted_at "Soft delete"
    }

    MG_PRODUCT_VARIANTS {
        ObjectId id PK
        ObjectId product_id FK
        string sku_code "UNIQUE"
        string tier_name
        decimal price
    }

    MG_INVENTORIES {
        ObjectId id PK
        string sku_code "UNIQUE"
        int stock_available
        int stock_locked
    }

    MG_CARTS {
        ObjectId id PK
        long customer_id FK "UNIQUE"
        int total_items
    }

    MG_CART_ITEMS {
        ObjectId id PK
        ObjectId cart_id FK
        string sku_code
        int quantity
    }
```

---

## 3. Order Domain — Order Service

```mermaid
erDiagram
    USERS ||--o{ PARENT_ORDERS : "buyer (1:N)"
    PARENT_ORDERS ||--o{ ORDERS : "1:N"
    USERS ||--o{ ORDERS : "seller (1:N)"
    ORDERS ||--o{ ORDER_ITEMS : "1:N"
    PARENT_ORDERS {
        BIGSERIAL id PK
        BIGINT customer_id FK
        DECIMAL final_amt
        TIMESTAMP created_at
    }

    ORDERS {
        BIGSERIAL id PK
        BIGINT parent_order_id FK
        BIGINT seller_id FK
        BIGINT customer_id FK
        VARCHAR order_code "UNIQUE"
        VARCHAR status "PENDING | PAID | SHIPPING | DELIVERED | CANCELLED | REFUNDED"
        INT version "Optimistic Lock"
    }

    ORDER_ITEMS {
        BIGSERIAL id PK
        BIGINT order_id FK
        VARCHAR sku_code
        VARCHAR name_snapshot
        DECIMAL price_snapshot
        INT quantity
        INT refunded_quantity
    }

```

---

## 4. Payment Domain — Payment Service

```mermaid
erDiagram
    USERS ||--|| SELLER_STRIPE_ACCOUNTS : "1:0..1"
    PARENT_ORDERS ||--o{ TRANSACTIONS : "1:N"
    TRANSACTIONS ||--o{ REFUNDS : "1:N"
    ORDERS ||--o{ REFUNDS : "1:N"
    REFUNDS ||--o{ REFUND_ITEMS : "1:N"
    ORDER_ITEMS ||--o{ REFUND_ITEMS : "1:N"
    ORDERS ||--o{ SELLER_TRANSFERS : "1:N"
    USERS ||--o{ SELLER_TRANSFERS : "receiver (1:N)"

    SELLER_STRIPE_ACCOUNTS {
        BIGSERIAL id PK
        BIGINT seller_id FK "UNIQUE"
        VARCHAR stripe_account_id "acct_xxx"
        VARCHAR account_status "PENDING | ACTIVE"
        BOOLEAN charges_enabled
        BOOLEAN payouts_enabled
    }

    TRANSACTIONS {
        BIGSERIAL id PK
        BIGINT parent_order_id FK
        DECIMAL amount
        VARCHAR method "STRIPE | VNPAY"
        VARCHAR trans_ref "PaymentIntent ID"
        VARCHAR status "SUCCESS | FAILED | REFUNDED"
    }

    REFUNDS {
        BIGSERIAL id PK
        BIGINT transaction_id FK
        BIGINT order_id FK
        VARCHAR type "FULL | PARTIAL"
        DECIMAL amount
        VARCHAR status "PENDING | SUCCESS | FAILED | REJECTED"
    }

    REFUND_ITEMS {
        BIGSERIAL id PK
        BIGINT refund_id FK
        BIGINT item_id FK
        INT quantity
        DECIMAL refund_amount
    }

    SELLER_TRANSFERS {
        BIGSERIAL id PK
        BIGINT order_id FK
        BIGINT seller_id FK
        DECIMAL transfer_amount
        VARCHAR status "PENDING | SUCCESS | FAILED | REVERSED"
    }
```

---

## 5. Flash Sale Domain

```mermaid
erDiagram
    FS_SESSIONS ||--o{ FS_ITEMS : "1:N"
    FS_SESSIONS ||--o{ FS_REMINDERS : "1:N"
    USERS ||--o{ FS_REMINDERS : "1:N"
    MG_PRODUCT_VARIANTS ||--o{ FS_ITEMS : "1:N (sku)"

    FS_SESSIONS {
        BIGSERIAL id PK
        VARCHAR name
        TIMESTAMP start_time
        TIMESTAMP end_time
        VARCHAR status "UPCOMING | ACTIVE | ENDED"
        TIMESTAMP deleted_at "Soft delete"
    }

    FS_ITEMS {
        BIGSERIAL id PK
        BIGINT session_id FK
        VARCHAR sku_code FK
        DECIMAL flash_price
        INT flash_stock
        INT limit_per_user
        INT sold_qty
        VARCHAR status "PENDING | APPROVED | REJECTED"
        INT version "Optimistic Lock"
    }

    FS_REMINDERS {
        BIGSERIAL id PK
        BIGINT customer_id FK
        BIGINT session_id FK
    }
```

---

## 6. AI Chat Domain

```mermaid
erDiagram
    CHAT_SESSIONS ||--o{ CHAT_MESSAGES : "1:N"
    CHAT_SESSIONS ||--o{ PENDING_CONFIRMATIONS : "1:N"
    CHAT_SESSIONS ||--o{ TOOL_CALL_LOGS : "1:N"
    CHAT_MESSAGES ||--o{ PENDING_CONFIRMATIONS : "1:N"

    CHAT_SESSIONS {
        UUID id PK
        varchar user_id FK
        session_status status "ACTIVE | CLOSED | EXPIRED"
        text context_summary
    }

    CHAT_MESSAGES {
        UUID id PK
        UUID session_id FK
        message_role role "USER | ASSISTANT | TOOL_CALL | TOOL_RESULT"
        text content
        int sequence_no "UNIQUE per session"
        int tokens_used
    }

    PENDING_CONFIRMATIONS {
        UUID id PK "confirm token"
        UUID session_id FK
        UUID message_id FK
        confirm_action action_type "CANCEL_ORDER | etc"
        jsonb payload
        confirm_status status "PENDING | CONFIRMED | REJECTED | EXPIRED"
    }

    TOOL_CALL_LOGS {
        UUID id PK
        UUID session_id FK
        varchar tool_name
        jsonb input_params
        jsonb output
        tool_call_status status
        int duration_ms
        smallint risk_level "1 | 2 | 3"
    }
```

---

## 7. Supporting Tables

```mermaid
erDiagram
    IMAGES ||--o{ MG_PRODUCT_IMAGES : "1:N"
    MG_PRODUCTS ||--o{ MG_PRODUCT_IMAGES : "1:N"
    USERS ||--o{ MG_NOTIFICATIONS : "1:N"

    IMAGES {
        UUID id PK
        text url "MinIO URL"
        varchar file_name
        int file_size "bytes"
    }

    MG_PRODUCT_IMAGES {
        BIGSERIAL id PK
        varchar product_id FK
        UUID image_id FK
        boolean is_main
        int sort_order
    }

    MG_NOTIFICATIONS {
        ObjectId id PK
        long user_id FK
        string title
        string body
        string type
        bool is_read
        date created_at "TTL 90 days"
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
    }

    FAILED_EVENTS {
        BIGSERIAL id PK
        VARCHAR topic_or_task
        JSONB payload
        TEXT error_reason
        VARCHAR status "PENDING | DEAD | RESOLVED"
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
        Rate limit counter"]
        RC2["ctx:{sessionId}
        TTL: 30 min
        Session message cache"]
        RC3["pending:{confirmId}
        TTL: 5 min
        Confirm fast-lookup"]
        RC4["buf:{sessionId}
        TTL: 10 min
        Product search buffer"]
        RC5["tool:cache:{hash}
        TTL: 60s
        Tool result cache"]
    end
```
