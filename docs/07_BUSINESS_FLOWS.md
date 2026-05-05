# Luồng Nghiệp Vụ Tổng Hợp — Business Flows Overview

**Phiên bản:** v1.0
**Ngày:** 2026-04-22
**Trạng thái:** Production-Ready
**Ngôn ngữ:** Tiếng Việt + English (Mermaid diagrams)

---

## Mục Lục

1. [Tổng Quan Kiến Trúc](#1-tổng-quan-kiến-trúc)
2. [Luồng Xác Thực & Tài Khoản](#2-luồng-xác-thực--tài-khoản)
3. [Luồng Sản Phẩm](#3-luồng-sản-phẩm)
4. [Luồng Giỏ Hàng](#4-luồng-giỏ-hàng)
5. [Luồng Đặt Hàng & Thanh Toán](#5-luồng-đặt-hàng--thanh-toán)
6. [Luồng Vận Chuyển & Giao Hàng](#6-luồng-vận-chuyển--giao-hàng)
7. [Luồng Hoàn Tiền & RTS](#7-luồng-hoàn-tiền--rts)
8. [Luồng Flash Sale](#8-luồng-flash-sale)
9. [Kafka Topics Overview](#9-kafka-topics-overview)

---

## 1. Tổng Quan Kiến Trúc

### 1.1 Services & Giao Tiếp

```mermaid
graph TB
    subgraph Frontend["Frontend (React)"]
        C[Customer App<br>:3000]
        S[Seller App<br>:3001]
        A[Admin App<br>:3002]
    end

    subgraph Gateway["Infrastructure"]
        GW[API Gateway<br>:8080]
        EU[Eureka<br>:8761]
    end

    subgraph AxonServices["Axon Services (CQRS + Event Sourcing)"]
        ORD[order-service<br>:8083<br>OrderAggregate<br>ParentOrderPaymentSaga<br>OrderProcessingSaga]
        PAY[payment-service<br>:8082<br>PaymentAggregate<br>RefundModule]
        FS[flashsale-service<br>:8085<br>FlashSaleAggregate]
        WRK[flashsale-service<br>:8085<br>FlashSaleAggregate<br>Cronjobs<br>(JOB-01/02/08/21)]
    end

    subgraph TraditionalServices["Traditional Services"]
        IDT[identity-service<br>:8081<br>PostgreSQL<br>JWT Auth]
        PRD[product-service<br>:8090<br>MongoDB<br>Carts]
        SCH[search-service<br>:8091<br>Elasticsearch]
        NTF[notification-service<br>:8092<br>MongoDB<br>Email/SMS]
    end

    subgraph Infra["Infrastructure"]
        KFK[Kafka<br>7.4.0]
        RED[Redis<br>7.0]
        PGS[PostgreSQL<br>15.4]
        MNG[MongoDB<br>6.0]
        AXN[AxonServer<br>:8124]
    end

    C & S & A --> GW
    GW --> EU
    GW --> IDT
    GW --> PRD
    GW --> ORD
    GW --> PAY
    GW --> FS
    GW --> SCH
    GW --> NTF

    ORD <--> KFK
    PAY <--> KFK
    FS <--> KFK
    WRK <--> KFK
    IDT <--> KFK
    PRD <--> KFK
    NTF <--> KFK

    ORD <--> AXN
    PAY <--> AXN
    FS <--> AXN

    PRD <--> RED
    FS <--> RED
    IDT <--> RED
```

### 1.2 Frontend → API Gateway Routes

```mermaid
graph LR
    FE["Frontend<br>(Customer / Seller / Admin)"]
    GW["API Gateway<br>:8080"]

    FE -->|"POST /api/v1/auth/**"| GW
    FE -->|"GET /api/v1/products/**"| GW
    FE -->|"POST /api/v1/cart/**"| GW
    FE -->|"POST /api/v1/orders/**"| GW
    FE -->|"GET /api/v1/flash-sale/**"| GW
    FE -->|"POST /api/v1/payments/**"| GW
    FE -->|"GET /api/v1/admin/**"| GW
    FE -->|"GET /api/v1/seller/**"| GW

    GW -->|"→ identity-service:8081"| GW
    GW -->|"→ product-service:8090"| GW
    GW -->|"→ order-service:8083"| GW
    GW -->|"→ payment-service:8082"| GW
    GW -->|"→ flashsale-service:8085"| GW
    GW -->|"→ search-service:8091"| GW
    GW -->|"→ notification-service:8092"| GW
```

---

## 2. Luồng Xác Thực & Tài Khoản

### 2.1 Đăng Ký & Đăng Nhập

Khi người dùng mới tham gia sàn:

```mermaid
sequenceDiagram
    actor User
    participant FE as Ứng dụng
    participant System as Hệ thống

    Note over User,System: ĐĂNG KÝ TÀI KHOẢN
    User->>FE: Điền thông tin đăng ký
    FE->>System: Gửi yêu cầu đăng ký
    System->>System: Tạo tài khoản mới<br>Trạng thái: Hoạt động
    System->>System: Gán vai trò: Người mua
    System-->>FE: Tài khoản tạo thành công
    FE-->>User: Chuyển hướng đến đăng nhập

    Note over User,System: ĐĂNG NHẬP
    User->>FE: Nhập email và mật khẩu
    FE->>System: Gửi yêu cầu đăng nhập
    System->>System: Xác minh mật khẩu
    System->>System: Kiểm tra trạng thái tài khoản
    System->>System: Tạo phiên hoạt động
    System-->>FE: Đăng nhập thành công
    FE-->>User: Vào trang chủ
```

**Quy trình:**
- Tài khoản hoạt động ngay lập tức
- Phiên làm việc được bảo vệ và có thể gia hạn


### 2.2 Khóa / Mở Khóa Tài Khoản

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: Tạo tài khoản mới

    ACTIVE --> LOCKED: Admin gọi<br>POST /admin/users/{id}/lock

    LOCKED --> ACTIVE: Admin gọi<br>POST /admin/users/{id}/unlock
    LOCKED --> ACTIVE: JOB-17<br>(locked_until <= NOW)

    LOCKED --> LOCKED: Admin gọi lại<br>POST /admin/users/{id}/lock<br>(update locked_until)

    note right of ACTIVE: JWT Revocation:<br>Identity Service thêm JTI vào<br>Redis blocklist ngay lập tức
    note right of LOCKED: Tài khoản bị khóa:<br>- Không đăng nhập được<br>- Không đặt hàng<br>- Đơn đang xử lý vẫn tiếp tục
```

### 2.3 Đăng Ký Trở Thành Seller

```mermaid
sequenceDiagram
    actor User
    participant FE as Frontend
    participant GW as API Gateway
    participant IDT as identity-service
    participant STR as Stripe

    User->>FE: Nhấn "Trở thành Seller"
    FE->>GW: POST /api/v1/seller/register
    GW->>IDT: StartSellerOnboarding
    IDT->>STR: POST /api/v1/stripe/onboarding/start
    STR-->>IDT: onboarding_url (hết hạn 24h)
    IDT->>IDT: Tạo SELLER_STRIPE_ACCOUNTS<br>status=PENDING
    IDT-->>GW: onboarding_url
    GW-->>FE: Redirect to Stripe
    FE->>User: Stripe KYC form

    User->>STR: Hoàn thành KYC
    STR-->>IDT: Webhook: account.updated<br>(details_submitted=true)
    IDT->>IDT: Cập nhật status=ACTIVE
    IDT->>IDT: Thêm role=SELLER
    STR-->>User: Xác nhận hoàn tất
```

---

## 3. Luồng Sản Phẩm

### 3.1 Vòng Đời Sản Phẩm

```mermaid
stateDiagram-v2
    [*] --> PENDING: Seller tạo sản phẩm mới

    PENDING --> APPROVED: Admin duyệt
    PENDING --> REJECTED: Admin từ chối<br>(ghi lý do)

    REJECTED --> PENDING: Seller sửa & gửi lại<br>(trong 90 ngày)
    REJECTED --> soft_deleted: JOB-16<br>(>90 ngày không sửa)

    soft_deleted --> [*]: JOB-10<br>(>30 ngày + không có đơn liên quan)

    APPROVED --> APPROVED: Seller cập nhật<br>(gửi lại duyệt)

    APPROVED --> APPROVED: Auto ẩn nếu<br>Seller bị suspend đăng bị

    note right of APPROVED: Được index vào<br>Elasticsearch<br>Hiển thị trong tìm kiếm
    note right of PENDING: Đang chờ Admin duyệt
```

### 3.2 Seller Tạo Sản Phẩm

```mermaid
sequenceDiagram
    actor Seller
    participant FE as Seller App
    participant GW as API Gateway
    participant PRD as product-service
    participant SCH as search-service
    participant NTF as notification-service
    participant IDT as identity-service

    Seller->>FE: Tạo sản phẩm mới
    FE->>GW: POST /api/v1/products
    GW->>PRD: ProductRequest
    PRD->>PRD: Validate Seller<br>(role=SELLER, stripe KYC done)
    PRD->>PRD: INSERT MG_PRODUCTS<br>status=PENDING
    PRD->>PRD: INSERT MG_INVENTORIES
    PRD->>IDT: Kafka: product.pending_review
    PRD-->>GW: ProductResponse
    GW-->>FE: 201 Created
    FE-->>Seller: "Đã gửi duyệt"

    IDT->>NTF: Gửi thông báo<br>Admin có sản phẩm mới
    NTF->>NTF: INSERT mg_notifications

    rect rgb(220, 220, 255)
        Note over Admin,PRD: ADMIN DUYỆT
        Admin->>FE: Duyệt sản phẩm
        FE->>GW: POST /admin/products/{id}/approve
        GW->>PRD: Approve product
        PRD->>PRD: UPDATE MG_PRODUCTS<br>status=APPROVED
        PRD->>SCH: Index vào Elasticsearch
        PRD->>IDT: Kafka: product.approved
        PRD-->>GW: Success
        GW-->>FE: Approved
        FE-->>Admin: "Đã duyệt"
    end
```

---

## 4. Luồng Giỏ Hàng

```mermaid
sequenceDiagram
    actor Buyer
    participant FE as Customer App
    participant GW as API Gateway
    participant PRD as product-service
    participant RED as Redis

    Note over Buyer,PRD: THÊM VÀO GIỎ HÀNG
    Buyer->>FE: Nhấn "Thêm vào giỏ"
    FE->>GW: POST /api/v1/cart/items
    GW->>PRD: AddCartItemRequest
    PRD->>PRD: Check product APPROVED<br>Check stock_available > 0
    PRD->>PRD: UPSERT Cart (MongoDB)<br>UPSERT CartItem
    PRD->>PRD: SET cart.updated_at = NOW()
    PRD-->>GW: CartItemResponse
    GW-->>FE: Success
    FE-->>Buyer: Giỏ hàng cập nhật

    Note over Buyer,PRD: FLASH SALE — Redis Counter
    Buyer->>FE: Thêm vào giỏ (Flash Sale item)
    FE->>GW: POST /api/v1/cart/items
    GW->>PRD: AddCartItemRequest (fs_item_id)
    PRD->>PRD: Check FS_ITEMS.status=APPROVED<br>Check session ACTIVE
    PRD->>RED: HSET fs:stock:{sessionId}:{itemId}
    PRD->>PRD: UPSERT Cart + CartItem
    PRD-->>GW: Success
    GW-->>FE: Success
```

---

## 5. Luồng Đặt Hàng & Thanh Toán

### 5.1 Tổng Quan Checkout (Multi-Vendor)

```mermaid
sequenceDiagram
    actor Buyer
    participant FE as Customer App
    participant GW as API Gateway
    participant ORD as order-service
    participant PRD as product-service
    participant PAY as payment-service
    participant STR as Stripe
    participant KFK as Kafka
    participant AXN as Axon Server

    Buyer->>FE: Nhấn "Đặt Hàng" (Checkout)
    FE->>GW: POST /api/v1/orders/checkout
    GW->>ORD: CheckoutRequest<br>(address_id, item_ids)
    ORD->>PRD: Kafka: order.cart_items.request<br>↔ cart_items.response
    PRD-->>ORD: CartItem[] (product info, price, seller)
    ORD->>ORD: Tách theo Seller<br>→ N đơn con (1 per Seller)<br>→ 1 đơn cha (PARENT_ORDER)
    ORD->>PRD: Kafka: inventory.adjusted<br>(stock_locked cho mỗi item)
    ORD->>AXN: Emit ParentOrderCheckoutCreatedEvent
    AXN-->>ORD: @StartSaga<br>ParentOrderPaymentSaga
    ORD->>KFK: Publish payment.requested
    ORD-->>GW: CheckoutResponse<br>(parent_order_id, orders[], payment_status=PENDING)
    GW-->>FE: CheckoutResponse
    FE-->>Buyer: Hiển thị trang thanh toán Stripe

    Note over PAY,STR: THANH TOÁN STRIPE
    FE->>STR: Stripe.confirmPayment()
    STR->>STR: Xử lý thanh toán<br>(1 giao dịch cho tổng amount)
    STR-->>PAY: Webhook: payment_intent.succeeded
    PAY->>PAY: UPDATE Transaction.status=SUCCESS
    PAY->>PAY: INSERT SELLER_TRANSFERS<br>(status=PENDING, mỗi Seller)
    PAY->>KFK: Publish payment.success<br>(parent_order_id)
    PAY-->>FE: Thành công

    Note over ORD,AXN: SAGA XỬ LÝ
    KFK->>ORD: payment.success
    ORD->>ORD: PaymentKafkaEventBridge
    ORD->>AXN: Emit ParentOrderPaymentSucceededEvent
    AXN-->>ORD: @SagaEventHandler<br>ParentOrderPaymentSaga
    ORD->>ORD: UPDATE orders.status=PAID<br>(tất cả đơn con)
    ORD->>AXN: Emit OrderPaidEvent (per sub-order)
    ORD->>AXN: @EndSaga (ParentOrderPaymentSaga)

    Note over Buyer,PAY: CHUYỂN TIỀN CHO SELLER (khi DELIVERED)
    ORD->>KFK: Publish order.delivered
    KFK->>PAY: order.delivered
    PAY->>STR: stripe.transfers.create<br>(mỗi Seller nhận net_amount)
    PAY->>PAY: UPDATE SELLER_TRANSFERS<br>status=SUCCESS
    PAY->>KFK: Publish stripe.transfer.created
    PAY-->>Seller: Thông báo nhận tiền
```

### 5.2 Saga State Machine

```mermaid
stateDiagram-v2
    [*] --> CHECKOUT_PENDING: Buyer checkout

    CHECKOUT_PENDING --> PAYMENT_REQUESTED: ParentOrderCheckoutCreatedEvent<br>@StartSaga

    PAYMENT_REQUESTED --> PAYMENT_SUCCEEDED: ParentOrderPaymentSucceededEvent<br>payment.success Kafka
    PAYMENT_REQUESTED --> PAYMENT_FAILED: ParentOrderPaymentFailedEvent<br>payment.failed Kafka

    PAYMENT_SUCCEEDED --> ORDERS_PAID: ParentOrderPaymentSaga<br>UPDATE orders → PAID
    PAYMENT_FAILED --> ORDERS_CANCELLED: ParentOrderPaymentSaga<br>UPDATE orders → CANCELLED

    ORDERS_PAID --> SHIPPING: Seller cập nhật tracking
    SHIPPING --> DELIVERED: Buyer xác nhận nhận hàng
    SHIPPING --> DELIVERED_AUTO: JOB-22 (>7 ngày, không RTS)
    SHIPPING --> RETURNED: Seller RTS

    DELIVERED --> REFUNDED: Buyer yêu cầu hoàn tiền<br>Admin duyệt
    DELIVERED --> PARTIALLY_REFUNDED: Hoàn một phần

    ORDERS_CANCELLED --> [*]: @EndSaga

    ORDERS_PAID --> [*]: @EndSaga (ParentPaymentSaga)
    RETURNED --> REFUNDED_AUTO: RTS auto-refund

    note right of PAYMENT_REQUESTED: Đơn chờ thanh toán<br>Timeout: 30 phút (thường)<br>Timeout: 10 phút (Flash Sale)
    note right of ORDERS_PAID: JOB-13 auto-cancel nếu<br>quá timeout mà chưa thanh toán
```

### 5.3 Parent Order vs Sub-Orders

```mermaid
graph LR
    subgraph Parent["PARENT_ORDER (1 record)"]
        PO["id: 55<br>buyer_id: 42<br>total_amount: 1,200,000<br>status: PAID"]
    end

    subgraph SubOrders["ORDERS (N records, 1 per Seller)"]
        O1["id: 100<br>seller_id: 5<br>amount: 700,000<br>status: PAID"]
        O2["id: 101<br>seller_id: 9<br>amount: 500,000<br>status: PAID"]
    end

    subgraph OrderItems["ORDER_ITEMS (1+ per sub-order)"]
        OI1["order_id: 100<br>product_id: A<br>qty: 2<br>price: 350,000"]
        OI2["order_id: 101<br>product_id: B<br>qty: 1<br>price: 500,000"]
    end

    PO --> O1
    PO --> O2
    O1 --> OI1
    O2 --> OI2

    style PO fill:#e1f5fe
    style O1 fill:#fff3e0
    style O2 fill:#fff3e0
```

---

## 6. Luồng Vận Chuyển & Giao Hàng

```mermaid
sequenceDiagram
    actor Seller
    actor Buyer
    participant FE_S as Seller App
    participant FE_C as Customer App
    participant GW as API Gateway
    participant ORD as order-service
    participant IDT as identity-service
    participant NTF as notification-service

    Note over Seller,ORD: SELLER CẬP NHẬT MÃ VẬN ĐƠN
    Seller->>FE_S: Nhập mã vận đơn
    FE_S->>GW: PUT /api/v1/orders/{id}/tracking
    GW->>ORD: UpdateTrackingRequest<br>(tracking_number, carrier)
    ORD->>ORD: Check order.status=PAID
    ORD->>ORD: UPDATE ORDERS<br>status=SHIPPING<br>tracking_number
    ORD->>KFK: Publish order.shipped
    KFK->>NTF: Gửi thông báo Buyer
    KFK->>IDT: Identity: order.shipped
    ORD-->>GW: OrderResponse
    GW-->>FE_S: Success
    FE_S-->>Seller: "Đơn đang giao"

    Note over Buyer,ORD: BUYER XÁC NHẬN NHẬN HÀNG
    Buyer->>FE_C: Nhấn "Đã Nhận Hàng"
    FE_C->>GW: POST /api/v1/orders/{id}/confirm-received
    GW->>ORD: ConfirmReceivedRequest
    ORD->>ORD: Check order.status=SHIPPING
    ORD->>ORD: UPDATE ORDERS<br>status=DELIVERED
    ORD->>KFK: Publish order.delivered
    KFK->>PAY: order.delivered<br>→ Stripe Transfer cho Seller
    KFK->>NTF: Thông báo Buyer + Seller

    Note over Buyer,ORD: AUTO-DELIVERED (JOB-22)
    Seller->>ORD: (Không làm gì — Buyer quên bấm)
    JOB22->>ORD: 7 ngày không xác nhận
    ORD->>ORD: UPDATE ORDERS.status=DELIVERED
    ORD->>KFK: Publish order.delivered<br>(autoDelivered=true)
    KFK->>PAY: order.delivered → Transfer

    Note over Seller,ORD: ⚠️ JOB-22 KHÔNG áp dụng cho đơn RTS
```

---

## 7. Luồng Hoàn Tiền & RTS

### 7.1 Buyer Yêu Cầu Hoàn Tiền

```mermaid
sequenceDiagram
    actor Buyer
    actor Admin
    participant FE_C as Customer App
    participant FE_A as Admin App
    participant GW as API Gateway
    participant ORD as order-service
    participant PAY as payment-service
    participant STR as Stripe
    participant IDT as identity-service
    participant NTF as notification-service

    Buyer->>FE_C: Yêu cầu hoàn tiền<br>(upload ảnh bằng chứng)
    FE_C->>GW: POST /api/v1/orders/{id}/refunds
    GW->>ORD: CreateRefundRequest<br>(reason, images[])
    ORD->>ORD: Validate<br>status=DELIVERED<br>within 7 days<br>no existing pending refund
    ORD->>ORD: INSERT REFUNDS<br>status=PENDING<br>initiated_by=BUYER
    ORD->>ORD: INSERT REFUND_ITEMS<br>(evidence_images → MinIO)
    ORD->>KFK: Publish refund.requested
    KFK->>PAY: refund.requested<br>(lưu vào Payment DB)
    KFK->>NTF: Thông báo Admin có refund mới
    ORD-->>GW: RefundResponse
    GW-->>FE_C: "Đã gửi yêu cầu"
    FE_C-->>Buyer: Refund đang chờ duyệt

    Note over Admin,PAY: ADMIN DUYỆT REFUND
    Admin->>FE_A: Duyệt refund<br>(nhập tracking_number nếu có)
    FE_A->>GW: POST /admin/refunds/{id}/approve
    GW->>PAY: AdminApproveRequest<br>(admin_note, adjust_amount,<br>tracking_number, caused_by)
    PAY->>STR: stripe.refunds.create()
    STR-->>PAY: refund_id
    PAY->>PAY: UPDATE REFUNDS.status=SUCCESS<br>tracking_number<br>reviewed_by
    PAY->>KFK: Publish refund.admin_approved
    KFK->>ORD: Cập nhật order.status<br>REFUNDED hoặc PARTIALLY_REFUNDED
    KFK->>NTF: Thông báo Buyer (tiền đang về)
    PAY-->>GW: Approved
    GW-->>FE_A: "Đã hoàn tiền"

    Note over Admin,PAY: ADMIN TỪ CHỐI REFUND
    Admin->>FE_A: Từ chối refund<br>(ghi lý do)
    FE_A->>GW: POST /admin/refunds/{id}/reject
    GW->>PAY: AdminRejectRequest
    PAY->>PAY: UPDATE REFUNDS.status=REJECTED
    PAY->>KFK: Publish refund.rejected
    KFK->>NTF: Thông báo Buyer
```

### 7.2 Return To Sender (RTS) — Tự Động Hoàn Tiền

```mermaid
sequenceDiagram
    actor Seller
    participant FE_S as Seller App
    participant GW as API Gateway
    participant ORD as order-service
    participant PRD as product-service
    participant PAY as payment-service
    participant STR as Stripe
    participant IDT as identity-service
    participant NTF as notification-service

    Note over Seller,PAY: ĐƠN BỊ HOÀN VỀ (shipper gọi 3 lần không nghe)
    Seller->>FE_S: Nhấn "Xác nhận hàng hoàn về"<br>(upload ảnh bằng chứng bắt buộc)
    FE_S->>GW: POST /api/v1/orders/{id}/return-to-sender
    GW->>ORD: RTSRequest<br>(return_tracking_number,<br>evidence_images[])
    ORD->>ORD: Validate order.status=SHIPPING

    par Xử lý song song trong 1 transaction
        ORD->>ORD: UPDATE ORDERS.status=RETURNED
        ORD->>ORD: INSERT REFUNDS<br>type=FULL<br>initiated_by=SELLER<br>refund_reason_type=RETURN_TO_SENDER
        ORD->>PRD: Kafka: inventory.adjusted<br>stock_available += quantity<br>(trả lại kho)
        ORD->>KFK: Publish order.returned
    end

    KFK->>PAY: order.returned (RTS)
    PAY->>STR: stripe.refunds.create()<br>(⚠️ KHÔNG cần Admin duyệt)
    STR-->>PAY: refund_id

    PAY->>PAY: UPDATE REFUNDS.status=SUCCESS<br>reviewed_by=SYSTEM
    PAY->>PAY: UPDATE TRANSACTIONS<br>(hoàn tiền Buyer)
    PAY->>KFK: Publish refund.rts_completed

    KFK->>NTF: Buyer: "Tiền đang được hoàn về..."
    KFK->>NTF: Seller: "Xác nhận thành công.<br>Tồn kho đã cộng lại."

    Note over PAY,STR: ⚠️ ĐIỂM KHÁC BIỆT QUAN TRỌNG
    Note over PAY,STR: RTS ≠ Buyer refund thông thường
    Note over PAY,STR: • RTS: Seller chịu trách nhiệm → tự động refund
    Note over PAY,STR: • Buyer refund: Admin duyệt thủ công
    Note over PAY,STR: • JOB-22 không áp dụng cho đơn RTS
```

### 7.3 So Sánh: Buyer Refund vs RTS

```mermaid
graph LR
    subgraph BuyerRefund["Hoàn Tiền Buyer"]
        B1["Điều kiện:<br>DELIVERED + ≤7 ngày"]
        B2["Init: BUYER<br>Admin duyệt ✓"]
        B3["Tracking number:<br>Optional"]
    end

    subgraph RTS["Return To Sender"]
        R1["Điều kiện:<br>SHIPPING"]
        R2["Init: SELLER<br>Tự động (không cần duyệt) ✓"]
        R3["Tracking number:<br>Bắt buộc"]
    end

    style B1 fill:#fff3e0
    style B2 fill:#fff3e0
    style B3 fill:#fff3e0
    style R1 fill:#e8f5e9
    style R2 fill:#e8f5e9
    style R3 fill:#e8f5e9
```

---

## 8. Luồng Flash Sale

### 8.1 Tổng Quan Flash Sale

```mermaid
sequenceDiagram
    actor Admin
    actor Seller
    actor Buyer
    participant FE_A as Admin App
    participant FE_S as Seller App
    participant FE_C as Customer App
    participant GW as API Gateway
    participant FS as flashsale-service
    participant PRD as product-service
    participant ORD as order-service
    participant RED as Redis

    Note over Admin,FS: ADMIN TẠO SESSION FLASH SALE
    Admin->>FE_A: Tạo Flash Sale Session
    FE_A->>GW: POST /api/v1/admin/flash-sale/sessions
    GW->>FS: CreateSessionRequest<br>(start_time, end_time)
    FS->>FS: INSERT FS_SESSIONS<br>status=UPCOMING
    FS-->>GW: SessionResponse
    GW-->>FE_A: "Session đã tạo"

    Note over Seller,FS: SELLER ĐĂNG KÝ ITEM
    Seller->>FE_S: Đăng ký sản phẩm vào Flash Sale
    FE_S->>GW: POST /api/v1/flash-sale/items
    GW->>FS: RegisterFsItemRequest<br>(session_id, product_id,<br>flash_price, flash_stock,<br>limit_per_user)
    FS->>FS: Validate Seller<br>(KYC done)
    FS->>FS: INSERT FS_ITEMS<br>status=PENDING
    FS-->>GW: ItemResponse
    GW-->>FE_S: "Đã đăng ký"

    Admin->>FE_A: Duyệt FS_ITEMS
    FE_A->>GW: POST /api/v1/admin/flash-sale/items/{id}/approve
    GW->>FS: Approve
    FS->>FS: UPDATE status=APPROVED
    FS->>KFK: Publish flash_sale.item_approved

    Note over FS,RED: JOB-01 — SESSION BẮT ĐẦU (15 phút trước: JOB-02 gửi nhắc nhở)
    JOB01->>FS: Đến giờ bắt đầu
    FS->>FS: UPDATE FS_SESSIONS<br>UPCOMING → ACTIVE
    FS->>RED: Seed Redis<br>fs:stock:{sessionId}:{itemId} = flash_stock
    FS->>RED: fs:user_limit:{sessionId}:{itemId}:{userId} = 0
    FS->>KFK: Publish flash_sale.session_started

    Note over Buyer,RED: BUYER MUA HÀNG FLASH SALE
    Buyer->>FE_C: Nhấn "Mua ngay"
    FE_C->>GW: POST /api/v1/flash-sale/sessions/{id}/buy
    GW->>FS: BuyRequest (item_id, quantity)
    FS->>FS: Check Buyer<br>(ACTIVE, verified)
    FS->>RED: GET fs:stock:{sessionId}:{itemId}
    alt Hết hàng
        FS-->>GW: 410 Gone
        GW-->>FE_C: "Hết hàng rồi!"
    end
    alt Còn hàng
        RED->>RED: DECR fs:stock:{sessionId}:{itemId}
        RED->>RED: Check limit_per_user<br>HINCR fs:user_limit:{sessionId}:{itemId}:{userId}
        alt Vượt giới hạn
            RED->>RED: INCR lại (hoàn tác)
            FS-->>GW: 429 Too Many Requests
        end
        alt Trong giới hạn
            GW->>ORD: Redirect sang checkout
            ORD->>ORD: Tạo đơn Flash Sale<br>timeout 10 phút
            ORD->>RED: (tiếp tục thanh toán)
        end
    end

    Note over FS,RED: JOB-01 — SESSION KẾT THÚC
    JOB01->>FS: Đến giờ kết thúc
    FS->>FS: UPDATE FS_SESSIONS<br>ACTIVE → ENDED
    FS->>RED: DEL fs:stock:{sessionId}:*
    FS->>RED: DEL fs:user_limit:{sessionId}:*
    FS->>PRD: Kafka: inventory.adjusted<br>(trả flash_stock chưa bán về kho)
    FS->>KFK: Publish flash_sale.session_ended
```

### 8.2 Anti-Oversell Mechanism

```mermaid
graph TD
    A["Buyer: POST /buy"] --> B{"fs:stock Redis > 0 ?"}

    B -->|Không| D["410 Hết hàng"]
    B -->|Có| E["DECR Redis counter<br>(Atomic)"]

    E --> F{"Redis counter < 0 ?"}
    F -->|Có| G["INCR lại<br>(hoàn tác)"]
    G --> D
    F -->|Không| H["Check limit_per_user<br>fs:user_limit:{userId}"]

    H --> I{"Đã vượt giới hạn ?"}
    I -->|Có| J["INCR lại<br>429 Too Many Requests"]
    I -->|Không| K["Tạo Order<br>(timeout 10 phút)"]

    K --> L["Tiếp tục thanh toán<br>(Stripe → Saga)"]

    style E fill:#e8f5e9
    style K fill:#e8f5e9
    style L fill:#c8e6c9

    Note over A,L: JOB-21 (mỗi 5 phút): Reconciliation Redis vs DB để sửa bất đồng bộ do Pod crash
```

---

## 9. Kafka Topics Overview

### 9.1 Event Flow Between Services

```mermaid
graph LR
    subgraph Producers["Event Producers"]
        IDT_E["identity-service"]
        PRD_E["product-service<br>(Products, Inventory)"]
        ORD_E["order-service<br>(Orders, Checkout)"]
        PAY_E["payment-service<br>(Payments, Refunds)"]
        FS_E["flashsale-service<br>(Sessions, Items)"]
        WRK_E["Distributed Cronjobs<br>(identity/flashsale/product<br>/order/payment-service)"]
    end

    subgraph Consumers["Event Consumers"]
        IDT_C["identity-service"]
        PRD_C["product-service<br>(Inventory, Carts)"]
        ORD_C["order-service<br>(Payment Result)"]
        PAY_C["payment-service<br>(Payment Requests)"]
        FS_C["flashsale-service<br//(Session Lifecycle)"]
        NTF_C["notification-service<br>(Email, SMS, In-app)"]
    end

    ORD_E -->|"payment.requested"| PAY_C
    PAY_E -->|"payment.success / payment.failed"| ORD_C
    ORD_E -->|"order.created / shipped / delivered"| IDT_C
    ORD_E -->|"order.created / shipped / delivered"| NTF_C
    ORD_E -->|"order.checkout_completed"| PRD_C
    ORD_E -->|"order.delivered"| PAY_C
    ORD_E -->|"order.returned"| PAY_C
    PRD_E -->|"inventory.adjusted"| ORD_C
    PRD_E -->|"product.pending_review / approved / rejected"| IDT_C
    PRD_E -->|"product.pending_review / approved"| NTF_C
    FS_E -->|"flash_sale.session_started / ended"| NTF_C
    FS_E -->|"flash_sale.session_started / ended"| PRD_C
    PAY_E -->|"refund.admin_approved / rejected / rts_completed"| ORD_C
    PAY_E -->|"refund.admin_approved / rejected / rts_completed"| NTF_C
    WRK_E -->|"account.auto_locked / unlocked"| NTF_C
    WRK_E -->|"order.auto_cancelled"| NTF_C
```

### 9.2 Topic Summary Table

| Topic | Producer | Consumers | Purpose |
|-------|----------|-----------|---------|
| `payment.requested` | order-service (Saga) | payment-service | Khởi tạo thanh toán Stripe |
| `payment.success` | payment-service | order-service | Thanh toán thành công |
| `payment.failed` | payment-service | order-service | Thanh toán thất bại |
| `order.created` | order-service | identity, notification | Đơn mới được tạo |
| `order.shipped` | order-service | notification | Đơn đang giao |
| `order.delivered` | order-service | identity, payment, notification | Đơn hoàn thành |
| `order.returned` | order-service | payment, notification | RTS - hàng hoàn về |
| `order.cancelled` | order-service | notification | Đơn bị hủy |
| `order.auto_cancelled` | order-service (JOB-13) | notification | Đơn auto hủy (timeout) |
| `order.checkout_completed` | order-service | product-service | Xóa cart items |
| `product.pending_review` | product-service | notification | Sản phẩm chờ duyệt |
| `product.approved` | product-service | identity, notification | Sản phẩm được duyệt |
| `product.rejected` | product-service | identity, notification | Sản phẩm bị từ chối |
| `inventory.adjusted` | product-service, order-service | order-service, flashsale | Điều chỉnh tồn kho |
| `refund.requested` | order-service | payment | Yêu cầu hoàn tiền |
| `refund.admin_approved` | payment-service | order, notification | Admin duyệt hoàn tiền |
| `refund.rts_completed` | payment-service | order, notification | RTS hoàn tiền tự động |
| `flash_sale.session_started` | flashsale-service | notification, product | Flash Sale bắt đầu |
| `flash_sale.session_ended` | flashsale-service | notification, product | Flash Sale kết thúc |
| `account.auto_locked` | worker (JOB-17) | notification | Tài khoản bị khóa tự động |
| `account.unlocked` | worker (JOB-17) | notification | Tài khoản được mở khóa |

---

## 📚 Related Documents

- **[03_BUSINESS.md](03_BUSINESS.md)** — Tài liệu nghiệp vụ chi tiết (9 workflows, policies, cronjobs)
- **[04_POLICIES.md](04_POLICIES.md)** — System policies chi tiết
- **[06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md)** — Saga implementation chi tiết
- **[08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md)** — Integration patterns chi tiết
- **[05_OPERATIONS.md](05_OPERATIONS.md)** — Cronjobs chi tiết

---

**Tài liệu tạo:** 2026-04-22
**Phiên bản:** v1.0
