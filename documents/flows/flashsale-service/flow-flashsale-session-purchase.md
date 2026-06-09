# Business Flow: Flash Sale Session, Item Registration, and Purchase
Scope: `flashsale-service` with identity/order/product/search edges

### Use Case Coverage

| Use case | Status against current code | Evidence | Notes |
|----------|-----------------------------|----------|-------|
| UC-FLASHSALE-001: Admin Create Session | Implemented | `FlashSaleController.createSession` line 46, `FlashSaleService.createSession` line 166 | Session creation persists `UPCOMING`, registers Redis ZSET start/end triggers, and publishes `flash_sale.session_created`. |
| UC-FLASHSALE-002: Seller Register Product | Implemented | `FlashSaleController.createFlashSaleItem` line 81, `FlashSaleService.createFlashSaleItem` line 127 | Items are auto-approved as `APPROVED`, publish `flash_sale.item_registered`, and notification copy matches the auto-approve behavior. |
| UC-FLASHSALE-003: View Sessions | Implemented | `FlashSaleController.getSessions` line 25, `getActiveSessions` line 32, `getSessionDetail` line 39 | List/detail and `GET /v1/flash-sales/active` are implemented. |
| UC-FLASHSALE-006: System End Session | Implemented | `FlashSaleSessionScheduler` line 39, lifecycle payload with `flashItems` line 92 | Scheduler starts/ends sessions and emits payloads that product-service can use for flash price sync by SKU. |

### Sequence Diagram

```mermaid
sequenceDiagram
    actor Admin
    actor Seller
    actor Buyer
    participant FS as Flashsale Service
    participant Redis as Redis
    participant Kafka as Kafka
    participant Identity as Identity Service
    participant Product as Product Service
    participant Order as Order Service
    participant Search as Search Service

    Admin->>FS: POST /v1/flash-sales
    FS->>FS: Persist flash sale session
    FS->>Redis: ZADD flash_sale:triggers start/end entries
    FS->>Kafka: flash_sale.session_created

    Seller->>FS: POST /v1/flash-sales/{sessionId}/items
    FS->>FS: Persist approved flash-sale item
    FS->>Kafka: flash_sale.item_registered

    FS->>FS: Scheduler detects due start/end
    FS->>Kafka: flash_sale.session_started or flash_sale.session_ended with flashItems
    Kafka->>Product: FlashSaleEventHandler applies or resets flash prices
    Kafka->>Search: Product/search events refresh visible pricing

    Buyer->>FS: GET /v1/flash-sales and /v1/flash-sales/active
    FS-->>Buyer: Sessions and details
    Buyer->>FS: POST /v1/flash-sales/{sessionId}/buy
    FS->>Redis: Lua decrement fs:stock:{itemId}
    FS->>Kafka: order.address_request
    Kafka->>Identity: AddressKafkaConsumer
    Identity->>Kafka: order.address_response
    Kafka->>FS: onAddressResponse
    FS->>Kafka: order.checkout_submitted
    Kafka->>Order: Create order from flash-sale checkout
```

### Implementation Notes

| Concern | Current behavior |
|---------|------------------|
| Session triggers | Redis ZSET triggers are registered on create; the DB scheduler remains the executable lifecycle worker. |
| Price sync payload | Start/end events include `flashItems` with `sku_code`, `flash_price`, and `flash_stock`; product-service also keeps legacy `flashPriceMap` support. |
| Reminder features | Reminder endpoints exist in code, but reminder-specific use cases are not part of the current active flashsale use-case set. |
