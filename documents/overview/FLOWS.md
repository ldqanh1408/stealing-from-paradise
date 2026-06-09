# Cross-Service Flow Diagrams

> Generated: 2026-05-10
> Source: `docs/services/flashsale-service/flashsale_service_flow.md`, `docs/services/product-service/product_service_flow.md`, KAFKA_EVENTS.md files

---

## 1. Flash Sale Lifecycle Flow

### 1.1 Session Creation (Admin)

```mermaid
sequenceDiagram
    actor Admin
    participant FS as Flash Sale Service
    participant PG as PostgreSQL
    participant Redis

    Admin->>FS: POST /flash-sales (name, start_time, end_time, discount)
    FS->>FS: Validate end_time > start_time
    FS->>FS: Validate 0 < discount <= 100
    FS->>FS: Calculate registration_deadline = start_time - 15min
    FS->>PG: INSERT fs_sessions (status=UPCOMING)
    FS->>Redis: ZADD flash_sale:triggers <start_ms> session_start
    FS->>Redis: ZADD flash_sale:triggers <end_ms> session_end
    FS-->>Admin: 201 Created (session object)
```

### 1.2 Seller Product Registration

```mermaid
sequenceDiagram
    actor Seller
    participant FS as Flash Sale Service
    participant PS as Product Service
    participant PG as PostgreSQL

    Seller->>FS: POST /flash-sales/{id}/items (product_id)
    FS->>FS: Validate session.status = UPCOMING
    FS->>FS: Validate NOW() < registration_deadline
    FS->>PS: Verify product belongs to seller
    PS-->>FS: OK (owner confirmed)
    FS->>PG: INSERT fs_items (session_id, product_id, discount_applied)
    FS-->>Seller: 201 Created (auto-approved)
```

### 1.3 Session Auto-Transition (Redis ZSET Worker)

```mermaid
sequenceDiagram
    participant Worker as Redis Worker
    participant Redis
    participant FS as Flash Sale Service
    participant PG as PostgreSQL
    participant Kafka
    participant PS as Product Service
    participant SS as Search Service

    loop Every 100ms
        Worker->>Redis: ZRANGEBYSCORE flash_sale:triggers -inf <now> LIMIT 0 10
        alt Trigger found (session_start)
            Worker->>Redis: ZREM flash_sale:triggers <trigger> (atomic)
            Worker->>PG: UPDATE fs_sessions SET status=ACTIVE
            Worker->>Kafka: flash_sale.session_started
            Kafka->>PS: Consume event
            PS->>PS: Query fs_items, calculate flash_price per SKU
            PS->>Kafka: flash_sale.price_sync (action=activate)
            Kafka->>SS: Consume, update ES with flash prices
        else Trigger found (session_end)
            Worker->>Redis: ZREM flash_sale:triggers <trigger> (atomic)
            Worker->>PG: UPDATE fs_sessions SET status=ENDED
            Worker->>Kafka: flash_sale.session_ended
            Kafka->>PS: Consume event, reset prices
            PS->>Kafka: flash_sale.price_sync (action=deactivate)
            Kafka->>SS: Consume, reset ES prices to original
        end
    end
```

### 1.4 Redis Trigger Latency Comparison

| Method | Max Latency | Precision |
|--------|-------------|-----------|
| Cron 1 min | 60,000ms | +/- 30s |
| Redis Trigger (sleep 100ms) | 100ms | +/- 50ms |
| Redis Blocking BZPOPMIN | 0ms | Exact |

---

## 2. Product Checkout Flow

### 2.1 Browse Catalog

```mermaid
sequenceDiagram
    actor Customer
    participant PS as Product Service
    participant PG as PostgreSQL
    participant ES as Elasticsearch

    Customer->>PS: GET /products?category=X&sort=price
    PS->>ES: Search query with filters
    ES-->>PS: Product listing (SKU-first, field-collapsed)
    PS-->>Customer: Paginated product cards (min price, thumbnail)
```

### 2.2 Product Detail with Variant Selection

```mermaid
sequenceDiagram
    actor Customer
    participant PS as Product Service
    participant PG as PostgreSQL

    Customer->>PS: GET /products/{id}
    PS->>PG: SELECT product + variants + images
    PS-->>Customer: Product detail, variant matrix, gallery
    Customer->>Customer: Select color/size combination
    Note over Customer: Frontend maps variant_attributes to specific variant
    Customer->>Customer: Shows variant-specific price, stock, image
```

### 2.3 Add to Cart

```mermaid
sequenceDiagram
    actor Customer
    participant PS as Product Service
    participant PG as PostgreSQL
    participant Kafka

    Customer->>PS: POST /cart/items (variant_id, quantity)
    PS->>PG: Check product_variant.stock_quantity >= quantity
    alt Stock insufficient
        PS-->>Customer: 422 Insufficient stock
    else Stock OK
        PS->>PG: UPSERT cart_item (price_snapshot = current price)
        PS-->>Customer: 200 Cart updated
    end
```

### 2.4 View Cart (Lazy Evaluation)

```mermaid
sequenceDiagram
    actor Customer
    participant PS as Product Service
    participant PG as PostgreSQL

    Customer->>PS: GET /cart
    PS->>PG: SELECT cart_items WHERE cart_id = customer's cart
    PS->>PG: Batch SELECT product_variants for all cart items
    loop Each cart item
        PS->>PS: Compare price_snapshot vs current price
        PS->>PS: Check stock_quantity
        PS->>PS: Check variant.status
    end
    PS-->>Customer: Cart with flags: price_changed, out_of_stock, unavailable
```

### 2.5 Checkout Preview

```mermaid
sequenceDiagram
    actor Customer
    participant PS as Product Service
    participant Redis

    Customer->>PS: POST /v1/cart/checkout/preview (item_ids[])
    PS->>PS: Validate ALL items (price, stock, status)
    alt Any validation fails
        PS-->>Customer: 409 Conflict (per-item details)
    else All valid
        PS->>Redis: SET preview_token TTL 10min
        PS-->>Customer: 200 (preview_token, expires_at, items summary)
    end
```

### 2.6 Place Order (Checkout Submit)

```mermaid
sequenceDiagram
    actor Customer
    participant PS as Product Service
    participant Redis
    participant OS as Order Service
    participant PayS as Payment Service

    Note over Customer,PayS: Step 1: Checkout Preview
    Customer->>PS: POST /v1/cart/checkout/preview (item_ids[])
    PS->>PS: Validate ALL items (price, stock, variant)
    alt Validation fails
        PS-->>Customer: 409 Conflict (per-item details)
    else All valid
        PS->>Redis: SET preview_token TTL 10min
        PS-->>Customer: 200 (preview_token, expires_at)
    end

    Note over Customer,PayS: Step 2: Checkout Submit
    Customer->>PS: POST /v1/cart/checkout/submit (preview_token, address_snapshot)
    PS->>PS: Validate preview_token (Redis)
    PS->>PS: Re-validate stock for ALL items
    PS->>Redis: Reserve stock (DECR + stock_reservation PENDING)
    PS->>Redis: SET checkout:session:{sessionId} TTL 15min
    PS->>OS: Kafka: order.checkout_submitted (session_id, items, address)
    PS-->>Customer: 200 (session_id, total)

    Note over Customer,PayS: Step 3: Order Creation + Saga
    OS->>OS: Create ParentOrder + N sub-orders
    OS->>PayS: Create payment intent
    PayS-->>OS: Payment intent created
    OS-->>Customer: Order confirmed (pending payment)

    Note over Customer,PayS: Step 4: Payment Resolution
    alt Payment success
        PayS->>OS: payment.success
        OS->>PayS: Kafka: order.paid
        OS->>OS: Update order PAID
        PayS->>PS: Kafka: order.paid
        PS->>PS: confirmReservation() â†’ CONFIRMED
    else Payment failed
        PayS->>OS: payment.failed
        OS->>PayS: Kafka: order.payment_failed
        OS->>OS: Update order CANCELLED
        PayS->>PS: Kafka: order.payment_failed
        PS->>PS: releaseReservation() â†’ RELEASED
    end
```

### 2.7 Payment Resolution

```mermaid
sequenceDiagram
    participant Stripe
    participant PayS as Payment Service
    participant Kafka
    participant OS as Order Service
    participant PS as Product Service

    Stripe->>PayS: Webhook payment_intent.succeeded
    PayS->>Kafka: payment.success
    Kafka->>OS: Consume â†’ update orders PAID
    Kafka->>PayS: order.paid
    PayS->>PS: Consume â†’ confirmReservation()

    alt Payment fails
        Stripe->>PayS: Webhook payment_intent.payment_failed
        PayS->>Kafka: payment.failed
        Kafka->>OS: Consume â†’ update orders CANCELLED
        Kafka->>PayS: order.payment_failed
        PayS->>PS: Consume â†’ releaseReservation()
    end
```

---

## 3. Order Lifecycle Flow

### 3.1 Full Order States

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT: Checkout initiated
    PENDING_PAYMENT --> PAID: payment.success
    PENDING_PAYMENT --> CANCELLED: payment.failed / timeout
    PAID --> SHIPPING: Seller ships
    SHIPPING --> DELIVERED: Buyer confirms receipt
    DELIVERED --> RETURNED: RTS flow
    RETURNED --> REFUNDED: Refund processed
```

### 3.2 Return To Sender (RTS) Flow

```mermaid
sequenceDiagram
    actor Seller
    participant OS as Order Service
    participant Kafka
    participant RefS as Refund Service
    participant PS as Product Service

    Seller->>OS: POST /orders/{id}/return-to-sender (tracking, carrier)
    OS->>OS: Update order status
    OS->>Kafka: order.returned
    Kafka->>RefS: Auto-create full refund
    Kafka->>PS: Restore stock (INCR Redis, UPDATE DB)
    Kafka->>RefS: Process Stripe refund (pre-payout / transfer reversal)
```

---

## 4. Product Lifecycle

```mermaid
stateDiagram-v2
    [*] --> active: Seller creates product
    active --> out_of_stock: All variants reach stock=0
    out_of_stock --> active: Any variant restocked
    active --> inactive: Seller unpublishes
    inactive --> active: Seller publishes
    out_of_stock --> inactive: Seller unpublishes
```

---

## 5. AI Chat Message Flow

```mermaid
sequenceDiagram
    actor User
    participant AC as AI Chat Service
    participant Redis
    participant LLM
    participant Tool as Core Services

    User->>AC: POST /chat (message) via SSE
    AC->>Redis: Check rate limit (rate:{userId})
    AC->>AC: INSERT chat_messages (role=USER)
    AC->>LLM: Send message + context
    LLM-->>AC: Tool call decision
    alt No tool needed
        LLM-->>AC: Stream tokens (delta events)
    else Tool needed (Muc 1/2)
        AC->>Tool: Execute tool (e.g., getOrderDetail)
        Tool-->>AC: Result
        AC->>AC: INSERT (TOOL_CALL, TOOL_RESULT)
        AC->>LLM: Send tool result
        LLM-->>AC: Stream tokens (delta events)
    else Tool needed (Muc 3)
        AC->>AC: INSERT pending_confirmation
        AC->>Redis: SET pending:{confirmId} (TTL 5min)
        AC-->>User: confirmation_required event
        User->>AC: POST /confirm (token, action)
        AC->>Tool: Execute confirmed action
        Tool-->>AC: Result
        AC-->>User: Stream result via SSE
    end
    AC-->>User: done event (messageId, tokensUsed)
```

---

