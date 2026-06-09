# Business Flow and Use Case Code Audit

**Audit date:** 2026-06-08  
**Scope:** `documents/use-cases` (48 Markdown files), `documents/flows` (12 Markdown files), and current Java backend code under `backend/`.  
**Docs folder note:** the repository uses `documents/` as the documentation root; there is no root `docs/` folder in this checkout.

## Method

The audit compared documented behavior against current code evidence from:

- REST controllers: `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`
- service/domain logic for state changes and validation rules
- Kafka producers and consumers: `@KafkaListener`, `KafkaTopics`, `kafkaTemplate.send`
- asynchronous orchestration: Axon sagas, schedulers, request/reply consumers, and replay logic
- verification command: `mvn -pl common-lib,identity-service,payment-service,product-service,order-service,refund-service,flashsale-service,notification-service,chat-service -am -DskipTests compile`

Status meanings:

- **Implemented**: the documented core behavior is evidenced in current code.
- **Implemented via alternate path**: business intent is implemented, but not through the exact endpoint/service named in the use case.
- **Code-only / docs gap**: code implements behavior that is not represented by the active use case set.

## Executive Summary

| Area | Documented use cases | Implemented | Implemented via alternate path | Code/doc variance | Fully absent |
|------|----------------------|-------------|--------------------------------|-------------------|--------------|
| AI chat service | 3 | 3 | 0 | 0 | 0 |
| Flashsale service | 4 | 4 | 0 | 0 | 0 |
| Identity service | 5 | 5 | 0 | 0 | 0 |
| Notification service | 3 | 3 | 0 | 0 | 0 |
| Order service | 8 | 8 | 0 | 0 | 0 |
| Payment service | 5 | 5 | 0 | 0 | 0 |
| Product service | 15 | 15 | 0 | 0 | 0 |
| Refund service | 3 | 2 | 1 | 0 | 0 |
| Search service | 2 | 2 | 0 | 0 | 0 |
| **Total** | **48** | **47** | **1** | **0** | **0** |

The previously identified P0/P1 drift has been resolved in backend code and reflected in the updated markdown flow/use-case documentation:

1. **Order paid cancellation:** buyer can cancel `PENDING` or unshipped `PAID`; seller can cancel unshipped `PAID` with a reason. Paid cancellation publishes `refund.full_requested` with `auto_process=true`, and refund-service auto-processes that request.
2. **Flash sale session mechanics:** create session now registers Redis ZSET triggers, publishes `flash_sale.session_created`, exposes `GET /v1/flash-sales/active`, auto-approves item registration, and publishes lifecycle payloads with `flashItems`.
3. **Notification realtime replay:** SSE remains live-sink based, while `Last-Event-ID` replay is backed by persisted Mongo notifications instead of Redis Pub/Sub.
4. **Identity events:** profile update publishes `account.updated`; seller registration publishes `seller.registered`; notification-service has a dedicated `SellerRegisteredConsumer`.
5. **Payment payout contract:** successful payouts publish `transfer.completed`; seller transfer history and balance endpoints are implemented.
6. **AI chat events/storage:** session/list endpoints and documented `ai.*` event aliases are implemented; pending confirmation storage is MongoDB with TTL.
7. **Product reject contract:** admin reject returns the updated `ProductResponse` instead of `Void`.

## Use Case Audit

### AI Chat Service

| Use case | Status | Code evidence | Audit note |
|----------|--------|---------------|------------|
| UC-AICHAT-001 Start New Chat Session | Implemented | `ChatController.createSession`, `ChatController.getActiveSessions`, `ChatService.createSession` | Session creation, close, history, active-session listing, and `ai.session.created/closed` events are implemented. |
| UC-AICHAT-002 Send Message | Implemented | `ChatController.chat`, `ChatService.streamChat`, `ChatService.publishMessageSent` | Streaming chat exists. The service publishes both legacy `ai_chat.message_sent` and documented `ai.chat.message_received`. |
| UC-AICHAT-003 Confirm Action | Implemented | `ChatController.confirm`, `ChatService.confirmAction`, `ChatService.publishConfirmationResolved` | Pending confirmations persist in MongoDB with TTL and publish both legacy resolved plus documented confirmed/rejected events. |

### Flashsale Service

| Use case | Status | Code evidence | Audit note |
|----------|--------|---------------|------------|
| UC-FLASHSALE-001 Create Session | Implemented | `FlashSaleController.createSession`, `FlashSaleService.createSession`, `registerSessionTriggers`, `publishSessionCreatedEvent` | Session creation sets `UPCOMING`, registers Redis ZSET triggers, and publishes `flash_sale.session_created`. |
| UC-FLASHSALE-002 Register Product | Implemented | `FlashSaleController.createFlashSaleItem`, `FlashSaleService.createFlashSaleItem`, `FlashSaleItem.status` default | Registration creates `APPROVED` items, publishes `flash_sale.item_registered`, and notification copy matches auto-approval. |
| UC-FLASHSALE-003 View Sessions | Implemented | `FlashSaleController.getSessions`, `getActiveSessions`, `getSessionDetail` | List/detail and `GET /v1/flash-sales/active` are implemented. |
| UC-FLASHSALE-006 End Session | Implemented | `FlashSaleSessionScheduler`, `FlashSaleEventHandler` in product-service | Scheduler publishes started/ended events with `flashItems`; product-service syncs flash pricing by SKU and keeps legacy map support. |

Code-only / docs gaps for flash sale:

- `POST /v1/flash-sales/{sessionId}/buy` is implemented, but there is no active `UC-FLASHSALE-005` purchase use case file.
- Item admin approve/reject endpoints and reminder endpoints are implemented, but the active flashsale use case set does not model those workflows.

### Identity Service

| Use case | Status | Code evidence | Audit note |
|----------|--------|---------------|------------|
| UC-IDENTITY-001 Register | Implemented | `AuthController.register`, `AuthService.registerUser` | User registration endpoint and service path exist. |
| UC-IDENTITY-002 Login | Implemented | `AuthController.login`, refresh/logout endpoints | Login, refresh, and logout endpoints exist. |
| UC-IDENTITY-003 Manage Profile | Implemented | `UserController.getCurrentUser`, `updateCurrentUser`, `UserService.updateUserProfile` | Profile APIs exist and `PUT /users/me` publishes `account.updated`. |
| UC-IDENTITY-004 Manage Addresses | Implemented | `UserController` address endpoints, address Kafka consumer | Address CRUD and order-service address request/reply are implemented. |
| UC-IDENTITY-006 Seller Register | Implemented | `AuthController.registerSeller`, `UserController.registerAsSeller`, `UserService.registerAsSeller`, `AuthService.publishSellerRegistered` | Seller registration publishes `seller.registered`; notification-service consumes it for seller welcome notification. |

### Notification Service

| Use case | Status | Code evidence | Audit note |
|----------|--------|---------------|------------|
| UC-NOTIF-001 Stream Notifications | Implemented | `NotificationController.stream`, `NotificationService.getNotificationStream`, `NotificationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc` | SSE works for connected users and replays missed persisted notifications after `Last-Event-ID`. |
| UC-NOTIF-002 View History | Implemented | `NotificationController.getNotifications`, `NotificationService.getNotifications` | History endpoint exists. |
| UC-NOTIF-003 Mark Read | Implemented | `NotificationController.markAsRead`, `markAllAsRead`, `NotificationService.markAsRead` | Current code supports the documented `PUT` method and compatibility `PATCH`. |

### Order Service

| Use case | Status | Code evidence | Audit note |
|----------|--------|---------------|------------|
| UC-ORDER-001 Checkout | Implemented | Product `CheckoutController.submit`, `CheckoutSubmitService`, `CheckoutSubmittedConsumer`, `OrderService.createOrderFromEvent` | Checkout is implemented through product-service async submit and Kafka. |
| UC-ORDER-002 View Orders | Implemented | `OrderController.getBuyerOrders`, detail endpoints | Buyer list and detail endpoints exist. |
| UC-ORDER-003 Cancel Order (Buyer) | Implemented | `OrderController.cancelOrder`, `OrderService.cancelOrder`, `publishAutoFullRefundRequested` | Buyer can cancel `PENDING` or unshipped `PAID`; paid cancel emits auto full-refund request. |
| UC-ORDER-004 Ship Order | Implemented | `OrderController.updateTracking`, `OrderService.updateTracking`, saga `ORDER_SHIPPED` | Seller shipping/tracking flow exists. |
| UC-ORDER-005 Confirm Delivery | Implemented | `OrderController.confirmReceived`, `OrderService.confirmReceived`, `OrderLifecycleScheduler` | Manual confirm and auto-deliver scheduler are implemented. |
| UC-ORDER-006 Request Return / RTS | Implemented | `RefundController` refund endpoints, `OrderController.returnToSender`, saga `ORDER_RETURNED_RTS` | Buyer refund and seller RTS paths are implemented through order-service and refund-service Kafka events. |
| UC-ORDER-007 View Seller Orders | Implemented | `OrderController.getSellerOrders`, dashboard endpoints | Seller order list and dashboard exist. |
| UC-ORDER-008 Seller Cancel Order | Implemented | `OrderService.cancelOrder`, `OrderProcessingSaga`, `RefundService.onRefundFullRequested` | Seller can cancel unshipped `PAID`; order-service emits seller cancellation and auto full-refund request. |

### Payment Service

| Use case | Status | Code evidence | Audit note |
|----------|--------|---------------|------------|
| UC-PAYMENT-001 Onboard Stripe | Implemented | `StripeOnboardingController`, `StripeOnboardingService`, onboarding scheduler | Start/status/refresh and URL cleanup exist. |
| UC-PAYMENT-002 Process Payment | Implemented | `PaymentRequestedConsumer`, `PaymentService` PaymentIntent logic | Payment request processing exists through Kafka and Stripe PaymentIntent logic. |
| UC-PAYMENT-003 Handle Webhook | Implemented | `PaymentController.stripeWebhook`, `PaymentService.handleWebhook` | Stripe webhook success/failure/account update handling exists. |
| UC-PAYMENT-007 Transfer to Seller | Implemented | `PaymentService.onOrderDelivered`, `PayoutScheduler.processEligiblePayouts`, `PayoutScheduler.publishPayoutEvent` | Delayed payout is implemented and successful payout publishes `transfer.completed`. |
| UC-PAYMENT-008 View Transfers | Implemented | `SellerPaymentsController.getSellerTransfers`, `getSellerBalance`, `SellerPaymentsService` | Dedicated `/seller/payments/transfers` and `/seller/payments/balance` endpoints are implemented. |

### Product Service

| Use case | Status | Code evidence | Audit note |
|----------|--------|---------------|------------|
| UC-PRODUCT-001 Browse Catalog | Implemented | `ProductController.listPublicProducts`, `SearchController.searchProducts` | Public listing and search-backed browsing exist. |
| UC-PRODUCT-002 Manage Categories | Implemented | `CategoryController`, `AdminCategoryController` | Public category reads and admin create/update/delete exist. |
| UC-PRODUCT-003 Create Product | Implemented | `ProductController.createProduct`, `ProductService.createProduct` | Seller product creation exists. |
| UC-PRODUCT-004 Manage Variants | Implemented | Variant endpoints, `VariantService` | Variant create/read/update/delete exists. |
| UC-PRODUCT-005 Upload Images | Implemented | Presigned URL and image endpoints | Presigned upload registration and image management exist. |
| UC-PRODUCT-006 Manage Stock | Implemented | `InventoryController`, `InventoryService` | Restock, adjust, and stock query exist. Inventory log endpoint remains a placeholder but is outside this use-case core behavior. |
| UC-PRODUCT-007 Reserve Stock | Implemented | `CartController.reserveStock`, `InventoryService.reserveStock`, `ReservationCleanupScheduler` | Reservation, release, and cleanup exist. |
| UC-PRODUCT-008 View Cart | Implemented | `CartController.getCart` | Cart view exists. |
| UC-PRODUCT-009 Add To Cart | Implemented | `CartController.addItem`, `CartService` | Add item exists. |
| UC-PRODUCT-010 Update Cart Item | Implemented | `CartController.updateItem` | Update quantity exists. |
| UC-PRODUCT-011 Remove From Cart | Implemented | `CartController.removeItem` | Remove item exists. |
| UC-PRODUCT-012 Submit Product Review | Implemented | `ProductController.submitForReview`, `ProductService.submitForReview` | Submit/review lifecycle exists. |
| UC-PRODUCT-013 List Pending Products | Implemented | `AdminProductController.getPendingProducts` | Admin pending list exists. |
| UC-PRODUCT-014 Approve Product | Implemented | `AdminProductController.approveProduct`, `ProductService.approveProduct` | Approval state and event exist. |
| UC-PRODUCT-015 Reject Product | Implemented | `AdminProductController.rejectProduct`, `ProductService.rejectProduct` | Reject state, reason, count, event, and full product response are implemented. |

### Refund Service

| Use case | Status | Code evidence | Audit note |
|----------|--------|---------------|------------|
| UC-REFUND-001 Create Refund | Implemented via alternate path | Order-service `RefundController`, refund-service consumers `RefundRequestedConsumer`, `RefundFullRequestedConsumer`, `OrderReturnedRtsConsumer` | Refund creation enters through order-service endpoints and Kafka. Direct public `POST /refunds` is intentionally not exposed by refund-service. |
| UC-REFUND-002 Approve Refund | Implemented | `AdminRefundController.approveRefund`, `RefundService.approveRefund` | Admin approval flow exists. |
| UC-REFUND-003 Reject Refund | Implemented | `AdminRefundController.rejectRefund`, `RefundService.rejectRefund` | Admin rejection flow exists. |

### Search Service

| Use case | Status | Code evidence | Audit note |
|----------|--------|---------------|------------|
| UC-SEARCH-001 Search Products | Implemented | `SearchController.searchProducts`, suggestions endpoint, product event consumers | Product search and suggestions exist. |
| UC-SEARCH-003 Reindex | Implemented | `SearchController.triggerReindex`, status endpoint, `ProductServiceClient` request/reply | Reindex request/reply flow exists. Reindex status is in-memory, so it resets on service restart. |

## Business Flow Audit

| Flow document | Status | Audit result |
|---------------|--------|--------------|
| `flows/ai-chat-service/flow-ai-chat-confirmation.md` | Implemented | Updated to document MongoDB pending confirmations and legacy + documented Kafka event aliases. |
| `flows/identity-service/flow-identity-access-profile.md` | Implemented | Updated to document `account.updated` and `seller.registered`. |
| `flows/notification-service/flow-notification-stream.md` | Implemented | Updated to document live Reactor sinks plus MongoDB-backed `Last-Event-ID` replay and PUT/PATCH read routes. |
| `flows/order-service/flow-order-lifecycle.md` | Implemented | Updated to document paid cancellation, auto full-refund request, Axon saga scope, and auto-delivery scheduler. |
| `flows/payment-service/flow-payment-stripe-payout.md` | Implemented | Updated to document transfer/balance endpoints and `transfer.completed`. |
| `flows/product-service/flow-product-catalog-cart-review.md` | Implemented | Updated to document reject response body. |
| `flows/refund-service/flow-refund-admin-review.md` | Implemented via current architecture | Admin list/detail/approve/reject exist. Direct public `POST /refunds` remains intentionally absent by current architecture. |
| `flows/search-service/flow-search-indexing.md` | Implemented | Search, event indexing, and reindex request/reply exist. Operational caveat: reindex status is in-memory. |
| `flows/flashsale-service/flow-flashsale-session-purchase.md` | Implemented | Updated to document Redis ZSET trigger registration, session events, active endpoint, auto-approved item registration, and product price sync payload. |
| `flows/cross-service/flow-order-cancellation.md` | Implemented | Updated to document buyer/seller paid cancellation and auto full-refund request. |
| `flows/cross-service/flow-refund-processing.md` | Implemented via order-service and Kafka | Refund creation enters through order-service and refund-service consumers; admin review and publish events exist. |
| `flows/cross-service/flow-stripe-onboarding.md` | Mostly implemented | Stripe onboarding APIs and scheduler exist. Any identity-service propagation event such as `seller.stripe_active` is not evidenced in current code and is not part of the active payment use cases. |

## Verification

| Check | Result |
|-------|--------|
| Backend compile for all changed modules | PASS: `mvn -pl common-lib,identity-service,payment-service,product-service,order-service,refund-service,flashsale-service,notification-service,chat-service -am -DskipTests compile` |
| Consumer modularity spot-check | PASS: order, payment, refund, flashsale, notification consumers are represented as focused classes rather than a single centralized consumer class. |
| Remaining P0/P1 gap search | No stale backend implementation gaps remain from the prior audit; remaining caveats are documented architecture choices or use-case exclusions. |

## Follow-Up Candidates

These are not blockers for the audited use-case set, but they are good next backlog items:

- Add explicit use cases for flash sale purchase, flash sale item admin review, and flash sale reminders, because code implements those features.
- Persist search reindex job status if operators need restart-stable progress tracking.
- Decide whether notification live delivery should become Redis Pub/Sub backed for multi-instance fan-out; current replay guarantee is persistence-backed.
