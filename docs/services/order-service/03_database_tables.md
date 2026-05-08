# Order Service — Database Tables

> Stack: PostgreSQL
> Cập nhật: 2026-05-06

---

## PARENT_ORDERS

Đơn hàng tổng (nhóm các đơn từ Seller khác nhau)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `customer_id` | BIGINT | FK → CUSTOMERS.id |
| `total_amt` | DECIMAL | Tổng tiền trước khuyến mãi |
| `final_amt` | DECIMAL | Số tiền thực thu |
| `status` | VARCHAR | PENDING_PAYMENT \| PAID \| CANCELLED |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## ORDERS

Đơn hàng chi tiết (từng Seller)

> **Giải thích `order_code`:** Mã hiển thị đơn hàng (public display code) dành cho khách hàng và người bán, có dạng dễ đọc như `DH20260506-1A2B3C`. Mã này khác với trường `id` (BIGSERIAL) chỉ dùng nội bộ.

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `parent_order_id` | BIGINT | FK → PARENT_ORDERS.id |
| `seller_id` | BIGINT | FK → SELLERS.id |
| `order_code` | VARCHAR | Unique, mã hiển thị đơn hàng |
| `customer_id` | BIGINT | FK → CUSTOMERS.id |
| `total_amt` | DECIMAL | Tổng tiền trước khuyến mãi |
| `final_amt` | DECIMAL | Số tiền thanh toán cuối |
| `status` | VARCHAR | PENDING \| PAID \| SHIPPING \| DELIVERED \| RETURNED \| REFUNDED \| PARTIALLY_REFUNDED \| CANCELLED |
| `cancelled_by` | VARCHAR | BUYER \| SELLER \| SYSTEM |
| `cancel_reason` | TEXT | Lý do hủy |
| `shipping_address` | JSONB | Snapshot địa chỉ giao hàng |
| `shipping_deadline` | TIMESTAMP | Hạn cập nhật mã vận đơn (created_at + 3 ngày) |
| `paid_at` | TIMESTAMP | Thời điểm thanh toán thành công |
| `shipped_at` | TIMESTAMP | Thời điểm seller xác nhận đã giao hàng (tất cả item có tracking) |
| `delivered_at` | TIMESTAMP | Thời điểm giao hàng thành công đến tay khách |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

> **Lưu ý:** Cột `tracking_number` đã được dời xuống bảng `ORDER_ITEMS` để hỗ trợ giao hàng từng phần (mỗi item/món hàng có thể có mã vận đơn riêng). `shipped_at` được cập nhật khi tất cả item đã có tracking, `delivered_at` khi toàn bộ đơn hoàn tất.

---

## ORDER_ITEMS

Chi tiết sản phẩm trong đơn hàng

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `order_id` | BIGINT | FK → ORDERS.id |
| `sku_code` | VARCHAR | Mã SKU tại thời điểm mua |
| `variant_id` | VARCHAR | FK → MG_PRODUCT_VARIANTS.id |
| `name_snapshot` | VARCHAR | Tên sản phẩm snapshot |
| `image_snapshot` | VARCHAR | Ảnh snapshot |
| `price_snapshot` | DECIMAL | Đơn giá snapshot |
| `quantity` | INT | Số lượng mua |
| `fs_item_id` | BIGINT | FK → FS_ITEMS.id, nullable |
| `tracking_number` | VARCHAR | Mã vận đơn cho món hàng |
| `carrier` | VARCHAR | Đơn vị vận chuyển (VD: GHTK, Viettel Post) |
| `created_at` | TIMESTAMP | Thời điểm tạo |
