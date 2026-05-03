# Order Service — Database Tables

> Cập nhật: 2026-05-03

---

## PARENT_ORDERS
Đơn hàng tổng (nhóm các đơn từ Seller khác nhau)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `customer_id` | BIGINT | FK → CUSTOMERS.id |
| `total_amt` | DECIMAL | Tổng tiền trước khuyến mãi |
| `loyalty_discount` | DECIMAL | Số tiền giảm từ điểm |
| `final_amt` | DECIMAL | Số tiền thực thu |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## ORDERS
Đơn hàng chi tiết (từng Seller)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `parent_order_id` | BIGINT | FK → PARENT_ORDERS.id |
| `seller_id` | BIGINT | FK → SELLERS.id |
| `order_code` | VARCHAR | Unique, mã hiển thị |
| `customer_id` | BIGINT | FK → CUSTOMERS.id |
| `total_amt` | DECIMAL | Tổng tiền trước khuyến mãi |
| `final_amt` | DECIMAL | Số tiền thanh toán cuối |
| `status` | VARCHAR | PENDING \| PAID \| SHIPPING \| DELIVERED \| RETURNED \| REFUNDED \| PARTIALLY_REFUNDED \| CANCELLED |
| `cancelled_by` | VARCHAR | BUYER \| SELLER \| SYSTEM |
| `cancel_reason` | TEXT | Lý do hủy |
| `is_flash_sale` | BOOLEAN | Từ Flash Sale hay không |
| `shipping_address` | JSONB | Snapshot địa chỉ giao hàng |
| `tracking_number` | VARCHAR | Mã vận đơn |
| `shipping_deadline` | TIMESTAMP | Hạn cập nhật mã vận đơn (created_at + 3 ngày) |
| `version` | INT | Optimistic Locking |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## ORDER_ITEMS
Chi tiết sản phẩm trong đơn hàng

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `order_id` | BIGINT | FK → ORDERS.id |
| `sku_code` | VARCHAR | Mã SKU tại thời điểm mua |
| `variant_id` | VARCHAR | FK → MG_PRODUCT_VARIANTS.id |
| `name_snapshot` | VARCHAR | Tên sản phẩm snapshot |
| `image_snapshot` | VARCHAR | Ảnh snapshot |
| `price_snapshot` | DECIMAL | Đơn giá snapshot |
| `quantity` | INT | Số lượng mua |
| `refunded_quantity` | INT | Số lượng đã hoàn |
| `fs_item_id` | BIGINT | FK → FS_ITEMS.id, nullable |
| `created_at` | TIMESTAMP | Thời điểm tạo |

---

## OUTBOX_EVENTS
Event Outbox Pattern (cho eventual consistency)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `topic` | VARCHAR | Tên topic/event |
| `payload` | JSONB | Nội dung event |
| `status` | VARCHAR | PENDING \| PROCESSED \| FAILED |
| `retry_count` | INT | Số lần retry |
| `processed_at` | TIMESTAMP | Thời điểm xử lý |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## FAILED_EVENTS
Lưu trữ event/task lỗi để xử lý thủ công

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `topic_or_task` | VARCHAR | Tên topic hoặc task |
| `payload` | JSONB | Payload bị lỗi |
| `error_reason` | TEXT | Lý do lỗi |
| `retry_count` | INT | Số lần retry |
| `status` | VARCHAR | PENDING \| DEAD \| RESOLVED \| MANUAL_INTERVENTION |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |
