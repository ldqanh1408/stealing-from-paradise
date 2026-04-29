# Entity Summary - All Services

Tổng hợp toàn bộ entities từ các microservices của hệ thống Stealing from Paradise.

**Cập nhật lần cuối:** 2026-04-29

---

## Table of Contents

1. [Identity Service](#identity-service)
2. [Product Service](#product-service)
3. [Order Service](#order-service)
4. [Flash Sale Service](#flash-sale-service)
5. [Notification Service](#notification-service)
6. [Worker Service](#worker-service)
7. [Search Service](#search-service)
8. [Entity Relationships](#entity-relationships)

---

## Identity Service

### 1. User
**Database:** PostgreSQL | **Collection:** `users`

Quản lý thông tin người dùng của hệ thống (Buyer/Seller/Admin).

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (PK) | IDENTITY | ID người dùng |
| username | String | UNIQUE, NOT NULL | Tên đăng nhập |
| email | String | UNIQUE, NOT NULL | Email đăng nhập |
| password | String | - | Mật khẩu (hashed) |
| phone | String | - | Số điện thoại |
| fullName | String | - | Tên đầy đủ |
| avatarUrl | String | - | URL ảnh đại diện |
| status | String | - | ACTIVE \| LOCKED \| SUSPENDED |
| trustScore | Integer | DEFAULT 80 | Điểm uy tín (0-100) |
| lockedUntil | LocalDateTime | - | Thời gian mở khóa (nếu bị khóa) |
| lockReason | String | - | Lý do khóa tài khoản |
| appealCount | Integer | DEFAULT 0 | Số lần kháng cáo |
| productPostingSuspended | Boolean | DEFAULT false | Cấm đăng sản phẩm |
| lastCancellationPenaltyAt | LocalDateTime | - | Lần cuối bị phạt hủy đơn |
| lastWarningAt | LocalDateTime | - | Lần cuối bị cảnh báo |
| lastPostingSuspensionAt | LocalDateTime | - | Lần cuối bị cấm đăng |
| reward10OrdersAccumulated | Integer | DEFAULT 0 | Số đơn hàng tích lũy (cho reward) |
| createdAt | LocalDateTime | NOT NULL | Thời gian tạo |
| updatedAt | LocalDateTime | NOT NULL | Thời gian cập nhật |
| version | Integer | @Version | Optimistic locking |

**Indexes:**
- `email` (UNIQUE)
- `username` (UNIQUE)

**Relationships:**
- ↔ Role (1 User : N Roles)
- ↔ Address (1 User : N Addresses)
- ↔ LoyaltyAccount (1 User : 1 Account)
- ↔ Appeal (1 User : N Appeals)
- ↔ TrustScoreLog (1 User : N Logs)

---

### 2. Address
**Database:** PostgreSQL | **Collection:** `addresses`

Địa chỉ giao hàng của người dùng.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (PK) | IDENTITY | ID địa chỉ |
| userId | Long (FK) | NOT NULL | ID người dùng |
| provinceId | Integer | NOT NULL | Mã tỉnh/thành |
| districtId | Integer | NOT NULL | Mã quận/huyện |
| fullAddress | String | NOT NULL, TEXT | Địa chỉ đầy đủ |
| isDefault | Boolean | DEFAULT false | Địa chỉ mặc định |
| createdAt | LocalDateTime | NOT NULL | Thời gian tạo |
| updatedAt | LocalDateTime | NOT NULL | Thời gian cập nhật |

---

### 3. Role
**Database:** PostgreSQL | **Collection:** `roles`

Vai trò của người dùng (BUYER, SELLER, ADMIN).

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (PK) | IDENTITY | ID role |
| userId | Long (FK) | NOT NULL | ID người dùng |
| roleName | String | NOT NULL | Tên role (BUYER \| SELLER \| ADMIN) |
| createdAt | LocalDateTime | NOT NULL | Thời gian tạo |
| updatedAt | LocalDateTime | NOT NULL | Thời gian cập nhật |

**Indexes:**
- `user_id` (UNIQUE)

---

### 4. LoyaltyAccount
**Database:** PostgreSQL | **Collection:** `loyalty_accounts`

Tài khoản điểm thưởng của người dùng.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (PK) | IDENTITY | ID tài khoản |
| userId | Long (FK) | UNIQUE, NOT NULL | ID người dùng |
| totalEarnedPoints | Integer | DEFAULT 0 | Tổng điểm kiếm được |
| availablePoints | Integer | DEFAULT 0 | Điểm khả dụng |
| usedPoints | Integer | DEFAULT 0 | Điểm đã sử dụng |
| expiredPoints | Integer | DEFAULT 0 | Điểm hết hạn |
| createdAt | LocalDateTime | NOT NULL | Thời gian tạo |
| updatedAt | LocalDateTime | NOT NULL | Thời gian cập nhật |
| version | Integer | @Version | Optimistic locking |

---

### 5. PointTransaction
**Database:** PostgreSQL | **Collection:** `point_transactions`

Lịch sử giao dịch điểm thưởng.

**Note:** Được tham chiếu từ LoyaltyService nhưng không có entity class chi tiết.

---

### 6. TrustScoreLog
**Database:** PostgreSQL | **Collection:** `trust_score_logs`

Lịch sử thay đổi điểm uy tín.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (PK) | IDENTITY | ID log |
| userId | Long (FK) | NOT NULL | ID người dùng |
| delta | Integer | NOT NULL | Thay đổi điểm (+/-) |
| eventCode | String | - | Mã sự kiện (e.g., "ORDER_CANCEL", "REFUND") |
| reason | String | TEXT | Lý do thay đổi |
| changedBy | String | - | Người/hệ thống thay đổi |
| createdAt | LocalDateTime | NOT NULL | Thời gian tạo |

**Indexes:**
- `user_id`

**Relationship:**
- ↔ Appeal (1 TrustScoreLog : N Appeals)

---

### 7. TrustScoreEventsConfig
**Database:** PostgreSQL | **Collection:** `trust_score_events_config`

Cấu hình sự kiện ảnh hưởng đến điểm uy tín.

**Note:** Được tham chiếu từ TrustScoreService nhưng không có entity class chi tiết.

---

### 8. Appeal
**Database:** PostgreSQL | **Collection:** `appeals`

Kháng cáo về quyết định khóa tài khoản/giảm điểm uy tín.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (PK) | IDENTITY | ID kháng cáo |
| userId | Long (FK) | NOT NULL | ID người dùng kháng cáo |
| trustScoreLogId | Long (FK) | NOT NULL | ID log uy tín liên quan |
| reason | String | NOT NULL, TEXT | Lý do kháng cáo |
| evidenceUrls | String | JSONB | URL chứng cứ (JSON array) |
| status | String | DEFAULT PENDING | PENDING \| APPROVED \| REJECTED |
| reviewedBy | Long | - | Admin đã xem xét |
| adminNote | String | TEXT | Ghi chú của admin |
| reviewedAt | LocalDateTime | - | Thời gian xem xét |
| createdAt | LocalDateTime | NOT NULL | Thời gian tạo |
| updatedAt | LocalDateTime | NOT NULL | Thời gian cập nhật |

**Indexes:**
- `user_id`
- `status`

---

### 9. UserBanHistory
**Database:** PostgreSQL | **Collection:** `user_ban_history`

Lịch sử khóa tài khoản (bị cấm).

**Note:** Được tham chiếu từ UserService nhưng không có entity class chi tiết.

---

## Product Service

### 1. Product
**Database:** MongoDB | **Collection:** `products`

Sản phẩm được đăng bán trên hệ thống.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | String (@Id) | - | MongoDB ObjectId |
| sellerId | Long | @Indexed | ID người bán |
| categoryId | String | @Indexed | ID danh mục (FK → categories) |
| name | String | - | Tên sản phẩm |
| description | String | - | Mô tả chi tiết |
| attributes | Map<String, Object> | - | Thuộc tính sản phẩm (JSON) |
| images | List<String> | - | Danh sách URL ảnh |
| isFlash | Boolean | - | Có phải Flash Sale không |
| status | String | - | PENDING \| APPROVED \| REJECTED |
| rejectReason | String | - | Lý do từ chối (nếu REJECTED) |
| stockAvailable | Integer | - | Tồn kho khả dụng |
| deletedAt | LocalDateTime | - | Thời gian xóa (soft delete) |
| createdAt | LocalDateTime | @CreatedDate | Thời gian tạo |
| updatedAt | LocalDateTime | @LastModifiedDate | Thời gian cập nhật |

**Indexes:**
- `seller_id` + `status` (Compound)
- `category_id` + `status` (Compound)

**Relationships:**
- ↔ ProductVariant (1 Product : N Variants)
- ↔ Category (N Products : 1 Category)
- ↔ Inventory (1:N relationship through SKU)

---

### 2. ProductVariant
**Database:** MongoDB | **Collection:** `product_variants`

Các biến thể của sản phẩm (màu, kích thước, dung lượng, etc.).

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | String (@Id) | - | MongoDB ObjectId |
| productId | String | @Indexed | ID sản phẩm (FK → products) |
| skuCode | String | @Indexed UNIQUE | Mã SKU duy nhất |
| tierName | String | - | Tên biến thể (e.g., "Black - 256GB") |
| price | BigDecimal | - | Giá bán |
| createdAt | LocalDateTime | @CreatedDate | Thời gian tạo |
| updatedAt | LocalDateTime | @LastModifiedDate | Thời gian cập nhật |

**Indexes:**
- `product_id` + `sku_code` (Compound, UNIQUE)

**Relationships:**
- ↔ Inventory (1 Variant : 1 Inventory through SKU)
- ↔ CartItem (1 Variant : N CartItems)

---

### 3. Category
**Database:** MongoDB | **Collection:** `categories`

Danh mục sản phẩm (phân cấp).

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | String (@Id) | - | MongoDB ObjectId |
| name | String | @Indexed | Tên danh mục |
| slug | String | @Indexed UNIQUE | Slug URL-friendly (e.g., "dien-thoai-di-dong") |
| parentId | String | @Indexed SPARSE | ID danh mục cha (NULL = root) |
| level | Integer | - | Mức cấp (0=root, 1=sub, etc.) |
| createdAt | LocalDateTime | @CreatedDate | Thời gian tạo |
| updatedAt | LocalDateTime | @LastModifiedDate | Thời gian cập nhật |

**Indexes:**
- `name`
- `slug` (UNIQUE)
- `parent_id` + `level` (Compound)

---

### 4. Inventory
**Database:** MongoDB | **Collection:** `inventories`

Quản lý tồn kho (phải dùng MongoDB `$inc` operator cho atomicity).

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | String (@Id) | - | MongoDB ObjectId |
| skuCode | String | @Indexed UNIQUE | Mã SKU (1:1 với ProductVariant) |
| productId | String | - | ID sản phẩm (FK → products) |
| stockTotal | Integer | - | Tổng tồn kho |
| stockLocked | Integer | - | Số lượng bị khóa (PENDING/PAID orders) |
| stockAvailable | Integer | - | Số lượng khả dụng (= total - locked - reserved) |
| stockFlashReserved | Integer | - | Số lượng dành cho Flash Sale |
| updatedAt | LocalDateTime | @LastModifiedDate | Thời gian cập nhật |

**⚠️ IMPORTANT:**
- Không có `@Version` - dùng `$inc` operator thay vì optimistic locking
- Mọi thao tác tăng/giảm stock BẮT BUỘC dùng atomic update

**Indexes:**
- `sku_code` (UNIQUE)

---

### 5. Cart
**Database:** MongoDB | **Collection:** `carts`

Giỏ hàng của người dùng (metadata only).

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | String (@Id) | - | MongoDB ObjectId |
| userId | Long | @Indexed UNIQUE | ID người dùng (1:1 cart per user) |
| totalItems | Integer | - | Tổng số item (denormalized) |
| createdAt | LocalDateTime | @CreatedDate | Thời gian tạo |
| updatedAt | LocalDateTime | @LastModifiedDate | Thời gian cập nhật |

**Design Decision:**
- Cart chỉ lưu metadata (userId, totals, timestamps)
- CartItems được lưu riêng → cho phép query/update độc lập

---

### 6. CartItem
**Database:** MongoDB | **Collection:** `cart_items`

Chi tiết các sản phẩm trong giỏ hàng.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | String (@Id) | - | MongoDB ObjectId |
| cartId | String | @Indexed | ID giỏ hàng (FK → carts) |
| userId | Long | @Indexed | ID người dùng (denormalized) |
| variantId | String | - | Logic ID của variant |
| skuCode | String | @Indexed | Mã SKU |
| fsItemId | Long | - | ID Flash Sale Item (nullable) |
| priceSnapshot | BigDecimal | - | Giá tại thời điểm thêm vào |
| quantity | Integer | - | Số lượng |
| addedAt | LocalDateTime | - | Thời gian thêm vào giỏ |

**Indexes:**
- `cart_id` + `user_id` (Compound)

---

## Order Service

### 1. ParentOrder
**Database:** PostgreSQL | **Collection:** `parent_orders`

Đơn hàng "cha" - tập hợp các đơn hàng con từ các seller khác nhau.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (PK) | SEQUENCE | ID đơn hàng cha |
| orderCode | String | UNIQUE, NOT NULL | Mã đơn hàng duy nhất |
| userId | Long | NOT NULL | ID người mua |
| totalAmt | BigDecimal | NOT NULL | Tổng tiền (trước discount) |
| loyaltyDiscount | BigDecimal | DEFAULT 0 | Discount từ loyalty points |
| loyaltyPointsUsed | Integer | DEFAULT 0 | Điểm thưởng sử dụng |
| finalAmt | BigDecimal | NOT NULL | Tổng tiền cuối cùng |
| addressId | Long | - | ID địa chỉ giao hàng |
| timeoutAt | LocalDateTime | - | Hạn thanh toán |
| createdAt | LocalDateTime | NOT NULL | Thời gian tạo |
| updatedAt | LocalDateTime | NOT NULL | Thời gian cập nhật |
| version | Integer | @Version | Optimistic locking |

**Relationships:**
- ↔ Order (1 ParentOrder : N Orders)

---

### 2. Order
**Database:** PostgreSQL | **Collection:** `orders`

Đơn hàng con (từ một seller cụ thể).

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (PK) | IDENTITY | ID đơn hàng |
| parentOrderId | Long (FK) | NOT NULL | ID đơn hàng cha |
| sellerId | Long | NOT NULL | ID người bán |
| sellerName | String | - | Tên người bán (snapshot) |
| orderCode | String | UNIQUE, NOT NULL | Mã đơn hàng duy nhất |
| userId | Long | NOT NULL | ID người mua |
| totalAmt | BigDecimal | NOT NULL | Tổng tiền |
| finalAmt | BigDecimal | NOT NULL | Tiền cuối cùng |
| status | String | DEFAULT PENDING | PENDING \| PAID \| SHIPPED \| DELIVERED \| CANCELLED |
| cancelledBy | String | - | BUYER \| SELLER \| SYSTEM |
| cancelReason | String | TEXT | Lý do hủy |
| cancelledAt | LocalDateTime | - | Thời gian hủy |
| deliveredAt | LocalDateTime | - | Thời gian giao |
| isFlashSale | Boolean | DEFAULT false | Có phải Flash Sale |
| shippingAddress | String | JSONB | Địa chỉ giao hàng (JSON) |
| trackingNumber | String | - | Mã tracking |
| carrier | String | - | Nhà vận chuyển |
| shippingDeadline | LocalDateTime | - | Hạn giao hàng |
| returnTrackingNumber | String | - | Mã tracking hàng trả |
| createdAt | LocalDateTime | NOT NULL | Thời gian tạo |
| updatedAt | LocalDateTime | NOT NULL | Thời gian cập nhật |
| version | Integer | @Version | Optimistic locking |

**Indexes:**
- `user_id`
- `seller_id`
- `parent_order_id`
- `status`

**Relationships:**
- ↔ OrderItem (1 Order : N OrderItems)

---

### 3. OrderItem
**Database:** PostgreSQL | **Collection:** `order_items`

Chi tiết sản phẩm trong đơn hàng.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (PK) | IDENTITY | ID item |
| orderId | Long (FK) | NOT NULL | ID đơn hàng |
| productId | String | - | ID sản phẩm (FK → products) |
| skuCode | String | NOT NULL | Mã SKU |
| variantId | String | - | ID biến thể |
| nameSnapshot | String | - | Tên sản phẩm (snapshot) |
| variantName | String | - | Tên biến thể (e.g., "Đỏ / XL") |
| imageSnapshot | String | - | URL ảnh (snapshot) |
| priceSnapshot | BigDecimal | - | Giá tại thời điểm mua |
| quantity | Integer | NOT NULL | Số lượng |
| refundedQuantity | Integer | DEFAULT 0 | Số lượng đã hoàn |
| fsItemId | Long | - | ID Flash Sale Item (nếu từ FS) |
| createdAt | LocalDateTime | NOT NULL | Thời gian tạo |

**Indexes:**
- `order_id`

---

## Flash Sale Service

### 1. FlashSaleSession
**Database:** PostgreSQL (R2DBC) | **Collection:** `fs_sessions`

Session Flash Sale (thời gian diễn ra).

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (@Id) | - | ID session |
| name | String | - | Tên session (e.g., "Flash Sale Hôm Nay") |
| startTime | LocalDateTime | - | Thời gian bắt đầu |
| endTime | LocalDateTime | - | Thời gian kết thúc |
| status | String | DEFAULT UPCOMING | UPCOMING \| ONGOING \| ENDED |
| deletedAt | LocalDateTime | - | Thời gian xóa (soft delete) |
| createdAt | LocalDateTime | @CreatedDate | Thời gian tạo |
| updatedAt | LocalDateTime | @LastModifiedDate | Thời gian cập nhật |

**Relationships:**
- ↔ FlashSaleItem (1 Session : N Items)

---

### 2. FlashSaleItem
**Database:** PostgreSQL (R2DBC) | **Collection:** `fs_items`

Sản phẩm tham gia Flash Sale.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (@Id) | - | ID item |
| sessionId | Long | - | ID session (FK → fs_sessions) |
| skuCode | String | - | Mã SKU (FK → product_variants) |
| flashPrice | BigDecimal | - | Giá Flash Sale |
| flashStock | Integer | - | Số lượng Flash Sale |
| limitPerUser | Integer | DEFAULT 1 | Giới hạn mua/người |
| soldQty | Integer | DEFAULT 0 | Số lượng đã bán |
| status | String | DEFAULT PENDING | PENDING \| APPROVED \| REJECTED \| ENDED |
| createdAt | LocalDateTime | @CreatedDate | Thời gian tạo |
| updatedAt | LocalDateTime | @LastModifiedDate | Thời gian cập nhật |
| version | Integer | @Version | Optimistic locking |

---

### 3. FlashSaleReminder
**Database:** PostgreSQL (R2DBC) | **Collection:** `fs_reminders`

Reminder/Notification về Flash Sale sắp diễn ra.

**Attributes:**
- userId: ID người dùng
- sessionId: ID session
- reminderSentAt: Thời gian gửi reminder
- (Các trường khác tương tự)

---

## Notification Service

### 1. Notification
**Database:** MongoDB | **Collection:** `notifications`

Thông báo cho người dùng.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | String (@Id) | - | MongoDB ObjectId |
| userId | Long | @Indexed | ID người dùng |
| type | String | - | ORDER_CREATED \| PAYMENT_SUCCESS \| REFUND_APPROVED \| etc. |
| title | String | - | Tiêu đề thông báo |
| message | String | - | Nội dung thông báo |
| isRead | Boolean | DEFAULT false | Đã đọc chưa |
| deeplink | String | - | Deeplink đến chi tiết |
| createdAt | LocalDateTime | @Indexed | Thời gian tạo (TTL: 90 days) |

**Indexes:**
- `user_id` + `is_read` (Compound)
- `created_at` (TTL Index, expireAfterSeconds: 7776000)

---

## Worker Service

### 1. OutboxEvent
**Database:** PostgreSQL | **Collection:** `outbox_events`

Outbox pattern - lưu sự kiện cần gửi qua Kafka.

**Attributes:**
| Field | Type | Constraint | Mô tả |
|-------|------|-----------|-------|
| id | Long (PK) | IDENTITY | ID event |
| topic | String | NOT NULL | Topic Kafka để gửi |
| payload | String | JSONB, NOT NULL | Payload sự kiện (JSON) |
| status | String | DEFAULT PENDING | PENDING \| PROCESSED \| FAILED |
| retryCount | Integer | DEFAULT 0 | Số lần retry |
| processedAt | LocalDateTime | - | Thời gian xử lý thành công |
| createdAt | LocalDateTime | NOT NULL | Thời gian tạo |
| updatedAt | LocalDateTime | NOT NULL | Thời gian cập nhật |

**Indexes:**
- `status`

---

### 2. FailedEvent
**Database:** PostgreSQL | **Collection:** `failed_events`

Sự kiện gửi Kafka thất bại sau nhiều retry.

**Note:** Được tham chiếu từ Worker Service nhưng không có entity class chi tiết.

---

### 3. ShedLock
**Database:** PostgreSQL | **Collection:** `shedlock`

Distributed lock cho scheduled tasks (ShedLock).

**Attributes:**
- name: Tên task
- lock_at_time: Thời gian khóa
- locked_at: Thời gian khóa được thiết lập
- locked_by: Instance ID khóa
- next_lock_at_time: Thời gian khóa tiếp theo

---

## Search Service

### 1. SearchProduct
**Database:** Elasticsearch | **Index:** `products`

Document Elasticsearch cho Full-Text Search (được đồng bộ từ Product Service qua Kafka).

**Attributes:**
| Field | Type | Index Type | Mô tả |
|-------|------|-----------|-------|
| id | String (@Id) | - | MongoDB ObjectId |
| name | String | Keyword | Tên sản phẩm |
| description | String | Text + Keyword | Mô tả (fulltext search) |
| sellerId | Long | Long | ID người bán |
| sellerName | String | Keyword | Tên người bán (denormalized) |
| categoryId | String | Keyword | ID danh mục |
| categoryName | String | Keyword | Tên danh mục (denormalized) |
| priceMin | Double | Double | Giá thấp nhất (từ variant) |
| priceMax | Double | Double | Giá cao nhất (từ variant) |
| stockAvailable | Integer | Integer | Tồn kho |
| isFlash | Boolean | Boolean | Có phải Flash Sale |
| status | String | Keyword | PENDING \| APPROVED \| REJECTED |
| images | List<String> | Keyword | URL ảnh |
| attributes | List<Map> | Nested | Thuộc tính sản phẩm |
| tags | List<String> | Keyword | Tag tìm kiếm |
| createdAt | LocalDateTime | Date | Thời gian tạo |
| updatedAt | LocalDateTime | Date | Thời gian cập nhật |

**Analysis:**
- analyzer: "standard" cho description
- versionType: EXTERNAL

---

## Entity Relationships

### ER Diagram (Mermaid)

```
User ||--o{ Address : has
User ||--o{ Role : has
User ||--|| LoyaltyAccount : has
User ||--o{ TrustScoreLog : has
User ||--o{ Appeal : creates
TrustScoreLog ||--o{ Appeal : references

Product ||--o{ ProductVariant : contains
Product }o--|| Category : belongs_to
ProductVariant ||--|| Inventory : has
ProductVariant ||--o{ CartItem : in

Cart ||--|| User : belongs_to
Cart ||--o{ CartItem : contains
CartItem ||--|| ProductVariant : references

ParentOrder ||--|| User : belongs_to
ParentOrder ||--o{ Order : contains
Order ||--o{ OrderItem : contains
OrderItem ||--|| ProductVariant : references

FlashSaleSession ||--o{ FlashSaleItem : has
FlashSaleItem ||--|| ProductVariant : references

Notification ||--|| User : sent_to

OutboxEvent ||--|| Kafka : publishes_to
SearchProduct ||--|| Elasticsearch : indexed_in
```

### Key Foreign Keys Summary

**PostgreSQL (Identity Service - Relational):**
- `users.id` ← `addresses.user_id`
- `users.id` ← `roles.user_id`
- `users.id` ← `loyalty_accounts.user_id`
- `users.id` ← `trust_score_logs.user_id`
- `users.id` ← `appeals.user_id`
- `trust_score_logs.id` ← `appeals.trust_score_log_id`

**PostgreSQL (Order Service - Relational):**
- `parent_orders.id` ← `orders.parent_order_id`
- `orders.id` ← `order_items.order_id`

**PostgreSQL (Flash Sale Service - Relational/R2DBC):**
- `fs_sessions.id` ← `fs_items.session_id`
- (via SKU) `product_variants.sku_code` ← `fs_items.sku_code`

**MongoDB (Product Service - Document):**
- `products._id` ← `product_variants.product_id`
- `categories._id` ← `products.category_id`
- `product_variants.sku_code` ← `inventories.sku_code` (1:1)
- `carts._id` ← `cart_items.cart_id`
- `product_variants.id` ← `cart_items.variant_id` (logical)

**MongoDB (Notification Service - Document):**
- (via user_id) `users.id` ← `notifications.user_id`

**Elasticsearch (Search Service):**
- `products._id` ← `search_products.id` (sync from MongoDB)

---

## Data Types Reference

### PostgreSQL Types
- **Integer:** 32-bit signed integer
- **Long:** 64-bit signed integer (BIGINT)
- **BigDecimal:** Decimal/Numeric with precision
- **String:** VARCHAR
- **LocalDateTime:** TIMESTAMP
- **Boolean:** BOOLEAN
- **Map/List/JSON:** JSONB (native JSON type)

### MongoDB Types
- **ObjectId:** 12-byte unique identifier
- **String:** Variable-length string
- **Integer/Long:** Numeric types
- **LocalDateTime:** ISODate
- **BigDecimal:** Double/Decimal128
- **List/Map:** Array/Object

### Elasticsearch Types
- **Keyword:** Exact match, not analyzed
- **Text:** Full-text search, analyzed
- **Long/Integer/Double:** Numeric types
- **Date:** ISO 8601 format
- **Nested:** Object array type

---

## Audit Fields Convention

Hầu hết entities đều có các trường audit sau:

| Field | Type | Mô tả |
|-------|------|-------|
| createdAt | LocalDateTime | Thời gian tạo (auto-set @PrePersist) |
| updatedAt | LocalDateTime | Thời gian cập nhật (auto-set @PreUpdate) |
| version | Integer (Optional) | Optimistic locking version |

---

## Notes

1. **Concurrency Control:**
   - PostgreSQL: Optimistic locking via `@Version` field (OrderItem không có)
   - MongoDB: Inventory dùng `$inc` operator thay vì version

2. **Soft Delete:**
   - Product: `deletedAt` field
   - FlashSaleSession: `deletedAt` field

3. **Denormalization:**
   - OrderItem: Chứa snapshot của product name, price, image
   - SearchProduct: Denormalize seller name, category name
   - CartItem: Denormalize user_id

4. **TTL (Time-to-Live):**
   - Notification: 90 days auto-expire via MongoDB TTL index

5. **Atomic Operations:**
   - Inventory: Phải dùng MongoDB `$inc` operator
   - LoyaltyAccount: Dùng optimistic locking
   - ParentOrder: Dùng optimistic locking

6. **Database Persistence:**
   - **PostgreSQL:** Identity, Order, Worker services + flash-sale (R2DBC)
   - **MongoDB:** Product, Notification services
   - **Elasticsearch:** Search service (read-only sync)

