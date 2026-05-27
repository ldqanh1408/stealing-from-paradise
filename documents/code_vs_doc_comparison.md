# Codebase vs. Project Documentation Comparison Report

This report outlines the structural, database, and logic discrepancies discovered during an exhaustive audit of the **stealing-from-paradise** microservice codebase against the official project documentation (e.g., `database-entities.md`, `KAFKA_CATALOG.md`, `KAFKA_REQUEST_REPLY.md`, and service guidelines).

---

## 1. Database & Storage Discrepancies

### 1.1 Product Service Database: MongoDB vs. PostgreSQL
*   **Documentation Claim (`database-entities.md` §3):** 
    Claims that all catalog and cart tables have migrated to **PostgreSQL**, replacing old `MG_*` MongoDB collections completely with SQL entities (`CATEGORY`, `PRODUCT`, `PRODUCT_VARIANT`, etc.) using UUIDs and standard foreign keys.
*   **Codebase Reality:** 
    `product-service` is still **100% MongoDB-based**. It contains no PostgreSQL configuration or JPA/JDBC repositories. All domain repositories (`ProductRepository`, `ProductVariantRepository`, `CategoryRepository`, `CartRepository`, `CartItemRepository`) extend `MongoRepository` and operate on MongoDB document collections.
*   **Impact:** **CRITICAL**. The documentation details a target or future state database model that has not yet been implemented in Java. Developers relying on the docs would write incorrect queries or assume SQL-compatible joins.

### 1.2 AI Chat Service: MongoDB vs. PostgreSQL + Redis
*   **Documentation Claim (`database-entities.md` §11 & `AGENTS.md` prompt):**
    Claims the AI chat support service uses **PostgreSQL** + **Redis** storing relational tables like `chat_sessions`, `chat_messages`, `pending_confirmations`, and `tool_call_logs` with JSONB columns.
*   **Codebase Reality:**
    `chat-service` is written using **Reactive Spring Data MongoDB**. It uses `ReactiveMongoRepository` for `ChatMessage`, `ChatSession`, `PendingConfirmation`, and `ToolCallLog` document collections.
*   **Impact:** **HIGH**. The database stack in documentation contradicts the actual running database, leading to confusion during environment setups or query design.

### 1.3 Identity Service: Profile Tables & User Roles
*   **Documentation Claim (`database-entities.md` §2):**
    Lists separate database tables `CUSTOMERS`, `SELLERS`, and `ADMINS` referencing `USERS.id`, and documents a `role` VARCHAR column directly on the `USERS` table.
*   **Codebase Reality:**
    *   The `User` entity has **no profile tables** (`customers`, `sellers`, `admins`) defined in Java.
    *   There is no `role` column on the `users` table in `User.java`.
    *   User roles are handled via a separate `@Entity` called `Role` mapping to the `roles` table, linked by `user_id` with a unique constraint (one role per user).
*   **Impact:** **MEDIUM**. The codebase simplifies the user profile structure by omitting empty profile tables and normalizing roles to a separate table, which differs from the ERD documentation.

### 1.4 Search Service: Index Naming
*   **Documentation Claim (`database-entities.md` §10):**
    Elasticsearch index is named `skus`.
*   **Codebase Reality:**
    In `SearchProduct.java`, the index is configured as `@Document(indexName = "products", ...)` in Java.
*   **Impact:** **LOW**. A minor index name naming difference.

---

## 2. API & Event Integration Gaps

### 2.1 Broken Flow: Missing Consumer for Refund Presigned URL (Bug)
*   **Documentation Claim (`KAFKA_REQUEST_REPLY.md` §5.7):**
    Defines `order.refund_presigned_url.request` and `order.refund_presigned_url.response` as a valid Kafka Request-Reply pair used by `order-service` to fetch a presigned upload URL from `payment-service` (or `refund-service` after split) for buyer evidence.
*   **Codebase Reality:**
    *   `order-service` sends a request to `order.refund_presigned_url.request` via its REST endpoint `GET /orders/{orderId}/refunds/presigned-url` and blocks waiting for a reply.
    *   **No service in the backend consumes `order.refund_presigned_url.request`**. 
    *   `payment-service` has no listener. `refund-service` only listens to `ORDER_REFUNDS_REQUEST` and has no code or MinIO service configuration for generating presigned URLs.
*   **Impact:** **CRITICAL (BUG)**. This REST endpoint is non-functional. Any client call to request a refund presigned URL will hit a 5-second timeout and fail with an HTTP 500 error.

### 2.2 Logic Loophole: Refund 7-Day Window Date Proxy
*   **Codebase Logic (`RefundController.java` line 582 in `order-service`):**
    Checks eligibility for refunds by verifying that the order is delivered within 7 days using `order.getUpdatedAt()` as a proxy for the delivery timestamp, because the `Order` entity does not have a dedicated `delivered_at` field.
*   **The Loophole:**
    If an order's status transitions to `PARTIALLY_REFUNDED` or gets updated subsequently for any other reason, the `updatedAt` field is updated to the current time. This resets/extends the 7-day refund window indefinitely, allowing buyers to request refunds past the 7-day limit from actual delivery.
*   **Impact:** **HIGH**. Potential financial leakage or business rule bypass in production.

### 2.3 Shared Database Schema Pattern for Refund Service
*   **Codebase Architecture:**
    `refund-service` reads directly from the `payment` schema's tables (e.g., `transactions` and `seller_transfers`) using Hibernate's `@Immutable` annotation and `@Table(name = "...", schema = "payment")` on its domain models.
*   **Documentation Alignment:**
    This direct schema query pattern is not explicitly highlighted in the high-level architecture documents, but represents a pragmatic performance optimization since both services share the same physical database instance under different schemas.

---

## 3. Services Registry & Port Drift

*   **Service Port Registries:**
    *   `refund-service` port is **8094** in `application.yaml`, which is not listed in `AGENTS.md` or general service directories.
    *   `chat-service` is named `chat-service` in the code, but referred to as `ai-chat-service` in ports lists and prompts.
*   **Stale Comments:**
    `order-service`'s `PaymentKafkaEventBridge.java` still has comments claiming it consumes `refund.admin_approved` from `payment-service` instead of the newly extracted `refund-service`.

---

## 4. Reconcile Plan & Recommendations

> [!IMPORTANT]
> The following actions are recommended to reconcile the codebase with the project documentation:

1.  **Fix Refund Presigned URL Bug:**
    Implement a consumer for `order.refund_presigned_url.request` inside `refund-service`. Equip `refund-service` with `MinioService` (ported from `product-service`) to generate MinIO presigned URLs and reply on `order.refund_presigned_url.response`.
2.  **Add `delivered_at` Column to `Order` Entity:**
    Add a dedicated `delivered_at` timestamp field to `Order` (and `PARENT_ORDERS`) to represent the exact delivery date, rather than relying on `updated_at`. Update the 7-day check in `order-service` to use `delivered_at`.
3.  **Update `database-entities.md` (MongoDB vs PostgreSQL):**
    Modify `database-entities.md` (or add warning notes) to clarify that `product-service` and `chat-service` are **currently implemented in MongoDB** for the MVP, and the PostgreSQL schemas are designated for a future migration phase.
4.  **Update Port/Naming Catalogs:**
    Update `AGENTS.md` and related overview files to list `refund-service` on port **8094** and standardize naming to `chat-service`.
