# Order & Payment API Test Cases (Controller Layer)

This document summarizes the controller tests added for Order and Payment APIs.

## 1) Test files

```
backend/order-service/src/test/java/com/flashsale/orderdomain/controller/OrderControllerTest.java
backend/order-service/src/test/java/com/flashsale/orderdomain/controller/RefundControllerTest.java
backend/payment-service/src/test/java/com/flashsale/paymentdomain/controller/PaymentControllerTest.java
backend/payment-service/src/test/java/com/flashsale/paymentdomain/controller/AdminRefundControllerTest.java
backend/payment-service/src/test/java/com/flashsale/paymentdomain/controller/StripeOnboardingControllerTest.java
```

---

## 2) ASCII map: endpoint -> test method

### OrderController

```
+-----------------------------------------------+----------------------------------------------+
| API Endpoint                                  | Test Method                                   |
+-----------------------------------------------+----------------------------------------------+
| POST /api/v1/orders/checkout                  | checkout_returnsCreated                       |
| GET  /api/v1/orders                           | getBuyerOrders_capsSizeTo100                  |
| GET  /api/v1/orders/{orderId}                 | getOrderDetail_returnsData                    |
| GET  /api/v1/orders/parent/{parentOrderId}    | getParentOrderDetail_returnsData              |
| POST /api/v1/orders/{orderId}/cancel          | cancelOrder_returnsData                       |
| PUT  /api/v1/orders/{orderId}/tracking        | updateTracking_returnsData                    |
| POST /api/v1/orders/{orderId}/confirm-received| confirmReceived_returnsData                   |
| POST /api/v1/orders/{orderId}/return-to-sender| returnToSender_buildsRequestAndCallsService   |
| GET  /api/v1/sellers/me/orders                | getSellerOrders_capsSizeTo100                 |
+-----------------------------------------------+----------------------------------------------+
```

### RefundController (order-service)

```
+--------------------------------------------------------+------------------------------------------------------+
| API Endpoint                                           | Test Method                                           |
+--------------------------------------------------------+------------------------------------------------------+
| POST /api/v1/orders/{orderId}/refunds                 | createPartialRefund_returnsCreatedAndPublishesEvent   |
| POST /api/v1/orders/parent/{parentOrderId}/refund     | createFullRefund_returnsCreatedAndPublishesEvent      |
| POST /api/v1/orders/parent/{parentOrderId}/refunds/...| createMultiSellerPartialRefund_returnsCreated...      |
| GET  /api/v1/orders/{orderId}/refunds                 | getOrderRefunds_returnsRefundList                     |
| GET  /api/v1/orders/refunds                           | getBuyerRefunds_returnsRefundList                     |
| GET  /api/v1/orders/parent/{parentOrderId}/refund     | getFullRefundStatus_returnsAggregatedResponse         |
+--------------------------------------------------------+------------------------------------------------------+
```

### PaymentController

```
+-----------------------------------------------------------+---------------------------------------------+
| API Endpoint                                              | Test Method                                  |
+-----------------------------------------------------------+---------------------------------------------+
| GET  /api/v1/payments/parent-order/{parentOrderId}        | getTransactionByParentOrder_returnsData      |
| GET  /api/v1/payments/parent-order/{parentOrderId}/...    | getClientSecret_returnsData                  |
| POST /api/v1/stripe/webhooks                              | handleStripeWebhook_returnsReceived          |
+-----------------------------------------------------------+---------------------------------------------+
```

### AdminRefundController

```
+----------------------------------------------+--------------------------------------+
| API Endpoint                                 | Test Method                           |
+----------------------------------------------+--------------------------------------+
| GET  /api/v1/admin/refunds                   | listRefunds_returnsData               |
| GET  /api/v1/admin/refunds/{refundId}        | getRefund_returnsData                 |
| POST /api/v1/admin/refunds/{refundId}/approve| approveRefund_returnsData             |
| POST /api/v1/admin/refunds/{refundId}/reject | rejectRefund_returnsSuccessMessage    |
+----------------------------------------------+--------------------------------------+
```

### StripeOnboardingController

```
+--------------------------------------------------+-----------------------------------+
| API Endpoint                                     | Test Method                        |
+--------------------------------------------------+-----------------------------------+
| POST /api/v1/stripe/onboarding/start             | startOnboarding_returnsCreated     |
| GET  /api/v1/stripe/onboarding/status            | getOnboardingStatus_returnsData    |
| POST /api/v1/stripe/onboarding/refresh-link      | refreshOnboardingLink_returnsData  |
+--------------------------------------------------+-----------------------------------+
```

---

## 3) ASCII flow (how controller tests are structured)

```
        +---------------------+
        | Build input DTO/user|
        +----------+----------+
                   |
                   v
        +---------------------+
        | Mock service/repo   |
        | behavior (Mockito)  |
        +----------+----------+
                   |
                   v
        +---------------------+
        | Call controller      |
        | method directly      |
        +----------+----------+
                   |
                   v
        +---------------------+
        | Assert HTTP status, |
        | ApiResponse data,   |
        | and side effects    |
        +---------------------+
```

---

## 4) Notes

- These are focused controller-layer tests with mocked dependencies.
- Infra-dependent context smoke tests were marked disabled in:
  - `backend/order-service/src/test/java/com/flashsale/orderdomain/OrderDomainApplicationTests.java`
  - `backend/payment-service/src/test/java/com/flashsale/paymentdomain/PaymentDomainApplicationTests.java`
- This keeps build/test execution stable in environments without PostgreSQL/Kafka/Stripe.
