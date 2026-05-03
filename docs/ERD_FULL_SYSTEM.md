# Toàn bộ Hệ thống — ER Diagram

```mermaid
erDiagram
    %% ==================== IDENTITY DOMAIN ====================
    USERS ||--o| CUSTOMERS : "1:1"
    USERS ||--o| SELLERS : "1:1"
    USERS ||--o| ADMINS : "1:1"
    USERS ||--o{ ADDRESSES : "has"
    USERS {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar phone UK
    }

    CUSTOMERS {
        bigint id PK
        bigint user_id FK,UK
    }

    SELLERS {
        bigint id PK
        bigint user_id FK,UK
    }

    ADMINS {
        bigint id PK
        bigint user_id FK,UK
    }

    ADDRESSES {
        bigint id PK
        bigint user_id FK
    }

    %% ==================== TRUST & MODERATION ====================
    TRUST_SCORE_EVENTS_CONFIG {
        bigint id PK
        varchar event_code UK
    }

    TRUST_SCORE_LOGS ||--o| TRUST_SCORE_EVENTS_CONFIG : "references"
    TRUST_SCORE_LOGS }o--|| USERS : "belongs"
    TRUST_SCORE_LOGS {
        bigint id PK
        bigint user_id FK
        varchar event_code FK
    }

    USER_BAN_HISTORY }o--|| USERS : "belongs"
    USER_BAN_HISTORY }o--o| ADMINS : "reviewed by"
    USER_BAN_HISTORY {
        bigint id PK
        bigint user_id FK
        bigint admin_id FK
    }

    APPEALS }o--|| USERS : "belongs"
    APPEALS }o--|| TRUST_SCORE_LOGS : "references"
    APPEALS }o--o| ADMINS : "reviewed by"
    APPEALS {
        bigint id PK
        bigint user_id FK
        bigint trust_score_log_id FK
        bigint reviewed_by FK
    }

    %% ==================== LOYALTY ====================
    LOYALTY_ACCOUNTS ||--|| CUSTOMERS : "1:1"
    LOYALTY_ACCOUNTS {
        bigint id PK
        bigint customer_id FK,UK
    }

    POINT_TRANSACTIONS }o--|| CUSTOMERS : "belongs"
    POINT_TRANSACTIONS }o--o| ORDERS : "references"
    POINT_TRANSACTIONS {
        bigint id PK
        bigint customer_id FK
        bigint order_id FK
    }

    %% ==================== CATALOG DOMAIN (MongoDB) ====================
    MG_CATEGORIES ||--o{ MG_CATEGORIES : "self: parent_id"
    MG_CATEGORIES ||--o{ MG_PRODUCTS : "contains"
    MG_CATEGORIES {
        string id PK "Mongo ObjectId"
        string slug UK
        string parent_id FK
    }

    MG_PRODUCTS ||--o{ MG_PRODUCT_VARIANTS : "has"
    MG_PRODUCTS ||--o{ MG_INVENTORIES : "has"
    MG_PRODUCTS {
        string id PK "Mongo ObjectId"
        bigint seller_id FK
        string category_id FK
    }

    MG_PRODUCT_VARIANTS {
        string id PK "Mongo ObjectId"
        string product_id FK
        uuid image_id FK
        varchar sku_code UK
    }

    MG_INVENTORIES ||--|| MG_PRODUCT_VARIANTS : "1:1 by sku_code"
    MG_INVENTORIES {
        string id PK "Mongo ObjectId"
        varchar sku_code FK,UK
        string product_id FK
    }

    IMAGES ||--o{ MG_PRODUCT_IMAGES : "used in"
    IMAGES {
        uuid id PK
    }

    MG_PRODUCT_IMAGES }o--|| MG_PRODUCTS : "belongs"
    MG_PRODUCT_IMAGES }o--|| IMAGES : "references"
    MG_PRODUCT_IMAGES {
        bigint id PK
        string product_id FK
        uuid image_id FK
    }

    %% ==================== CART DOMAIN (MongoDB) ====================
    MG_CARTS ||--o{ MG_CART_ITEMS : "contains"
    MG_CARTS {
        string id PK "Mongo ObjectId"
        bigint customer_id FK,UK
    }

    MG_CART_ITEMS {
        string id PK "Mongo ObjectId"
        string cart_id FK
        string variant_id FK
        bigint fs_item_id FK
    }

    %% ==================== FLASH SALE DOMAIN ====================
    FS_SESSIONS ||--o{ FS_ITEMS : "contains"
    FS_SESSIONS ||--o{ FS_REMINDERS : "has reminders"
    FS_SESSIONS {
        bigint id PK
    }

    FS_ITEMS ||--|| MG_PRODUCT_VARIANTS : "references sku_code"
    FS_ITEMS {
        bigint id PK
        bigint session_id FK
        varchar sku_code FK
    }

    FS_REMINDERS {
        bigint id PK
        bigint customer_id FK
        bigint session_id FK
    }

    %% ==================== ORDER DOMAIN ====================
    PARENT_ORDERS ||--o{ ORDERS : "split into"
    PARENT_ORDERS {
        bigint id PK
        bigint customer_id FK
    }

    ORDERS ||--o{ ORDER_ITEMS : "contains"
    ORDERS {
        bigint id PK
        bigint parent_order_id FK
        bigint seller_id FK
        bigint customer_id FK
        varchar order_code UK
    }

    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        string variant_id FK
        bigint fs_item_id FK
    }

    %% ==================== PAYMENT DOMAIN ====================
    SELLER_STRIPE_ACCOUNTS ||--|| SELLERS : "1:1"
    SELLER_STRIPE_ACCOUNTS {
        bigint id PK
        bigint seller_id FK,UK
    }

    TRANSACTIONS ||--o{ REFUNDS : "has refunds"
    TRANSACTIONS {
        bigint id PK
        bigint parent_order_id FK
    }

    SELLER_TRANSFERS }o--|| ORDERS : "for order"
    SELLER_TRANSFERS }o--|| SELLERS : "to seller"
    SELLER_TRANSFERS {
        bigint id PK
        bigint order_id FK
        bigint seller_id FK
    }

    REFUNDS ||--o{ REFUND_ITEMS : "contains"
    REFUNDS }o--|| TRANSACTIONS : "from transaction"
    REFUNDS }o--|| ORDERS : "for order"
    REFUNDS }o--o| ADMINS : "reviewed by"
    REFUNDS {
        bigint id PK
        bigint transaction_id FK
        bigint order_id FK
        uuid group_ref
        bigint reviewed_by FK
    }

    REFUND_ITEMS {
        bigint id PK
        bigint refund_id FK
        bigint item_id FK
    }

    %% ==================== REVIEW DOMAIN ====================
    REVIEWS ||--o{ REVIEW_MEDIA : "has media"
    REVIEWS }o--|| MG_PRODUCTS : "for product"
    REVIEWS }o--|| ORDER_ITEMS : "for order_item"
    REVIEWS {
        uuid id PK
        string product_id FK
        bigint customer_id FK
        bigint order_item_id FK,UK
    }

    REVIEW_MEDIA {
        uuid id PK
        uuid review_id FK
        uuid image_id FK
    }

    REVIEW_SUMMARY ||--|| MG_PRODUCTS : "1:1"
    REVIEW_SUMMARY {
        uuid id PK
        string product_id FK,UK
    }

    %% ==================== NOTIFICATION DOMAIN (MongoDB) ====================
    MG_NOTIFICATIONS {
        string id PK "Mongo ObjectId"
        bigint user_id FK
    }

    %% ==================== AI CHAT DOMAIN ====================
    CHAT_SESSIONS ||--o{ CHAT_MESSAGES : "has"
    CHAT_SESSIONS ||--o{ PENDING_CONFIRMATIONS : "has"
    CHAT_SESSIONS ||--o{ TOOL_CALL_LOGS : "has"
    CHAT_SESSIONS {
        uuid id PK
        varchar user_id
    }

    CHAT_MESSAGES ||--o{ PENDING_CONFIRMATIONS : "references"
    CHAT_MESSAGES ||--o{ TOOL_CALL_LOGS : "references"
    CHAT_MESSAGES {
        uuid id PK
        uuid session_id FK
    }

    PENDING_CONFIRMATIONS {
        uuid id PK
        uuid session_id FK
        uuid message_id FK
    }

    TOOL_CALL_LOGS {
        uuid id PK
        uuid session_id FK
        uuid message_id FK
    }

    %% ==================== INFRASTRUCTURE ====================
    OUTBOX_EVENTS {
        bigint id PK
    }

    FAILED_EVENTS {
        bigint id PK
    }

    SHEDLOCK {
        varchar name PK
    }

    %% ==================== SEARCH (Elasticsearch) ====================
    ES_PRODUCTS_INDEX {
        string id PK "Mongo ObjectId"
        bigint seller_id
        string category_id
    }
```

---

## Chú thích

| Ký hiệu | Ý nghĩa |
|---------|---------|
| `||--||` | 1:1 |
| `||--o{` | 1:N (bắt buộc) |
| `}o--||` | N:1 (tùy chọn) |
| `}o--o{` | N:N |
| `FK` | Foreign Key |
| `UK` | Unique Key |
| `PK` | Primary Key |

### Domain Ownership

| Domain | Database | Service |
|--------|----------|---------|
| Identity | PostgreSQL | identity-service |
| Trust & Moderation | PostgreSQL | identity-service |
| Loyalty | PostgreSQL | identity-service |
| Catalog | MongoDB | product-service |
| Media Bridge | PostgreSQL | product-service |
| Cart | MongoDB | product-service |
| Flash Sale | PostgreSQL | flashsale-service |
| Order | PostgreSQL | order-service |
| Payment | PostgreSQL | payment-service |
| Review | PostgreSQL | order-service |
| Notification | MongoDB | notification-service |
| AI Chat | PostgreSQL | ai-chat-service |
| Infrastructure | PostgreSQL | worker-service |
| Search | Elasticsearch | search-service |

### Cross-Domain References

| Table | FK đến Domain khác |
|-------|---------------------|
| MG_PRODUCTS.seller_id | → Identity.SELLERS.id |
| MG_PRODUCTS.category_id | → Catalog.MG_CATEGORIES.id |
| MG_CART_ITEMS.fs_item_id | → FlashSale.FS_ITEMS.id |
| FS_ITEMS.sku_code | → Catalog.MG_PRODUCT_VARIANTS.sku_code |
| ORDER_ITEMS.variant_id | → Catalog.MG_PRODUCT_VARIANTS.id |
| ORDER_ITEMS.fs_item_id | → FlashSale.FS_ITEMS.id |
| POINT_TRANSACTIONS.order_id | → Order.ORDERS.id |
| REVIEWS.product_id | → Catalog.MG_PRODUCTS.id |
| MG_NOTIFICATIONS.user_id | → Identity.USERS.id |
