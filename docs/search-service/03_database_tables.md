# Search Service — Database Tables

> Cập nhật: 2026-05-03

---

## ES_PRODUCTS_INDEX (Elasticsearch)
Đánh chỉ mục sản phẩm cho tìm kiếm

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | VARCHAR | Mongo ObjectId — keyword |
| `name` | TEXT | Tên sản phẩm — text |
| `description` | TEXT | Mô tả — text |
| `seller_id` | BIGINT | ID người bán — long |
| `seller_name` | VARCHAR | Tên shop — text |
| `category_id` | VARCHAR | ID danh mục — keyword |
| `category_name` | VARCHAR | Tên danh mục — keyword |
| `price_min` | DECIMAL | Giá nhỏ nhất — double |
| `price_max` | DECIMAL | Giá lớn nhất — double |
| `stock_available` | INT | Tồn kho khả dụng — integer |
| `is_flash` | BOOLEAN | Có đang Flash Sale — boolean |
| `status` | VARCHAR | Trạng thái — keyword |
| `images` | JSONB | Danh sách ảnh — keyword[] |
| `attributes` | JSONB | Thuộc tính động — nested |
| `tags` | JSONB | Tag tìm kiếm — keyword[] |
| `created_at` | TIMESTAMP | Thời điểm tạo — date |
| `updated_at` | TIMESTAMP | Cập nhật cuối — date |

---

## Kafka Topics

| Topic | Mục đích |
|-------|----------|
| `product.changes` | Realtime sync DB → Elasticsearch |

### Event types

| Type | ES action | Khi nào |
|------|-----------|---------|
| `CREATED` | Full index document | Sản phẩm mới tạo |
| `UPDATED` | Full reindex | Tên, mô tả thay đổi |
| `DELETED` | Update `is_active = false` | Soft delete |
| `STOCK_CHANGED` | Partial update: `in_stock` | Tồn kho thay đổi |
| `PRICE_CHANGED` | Partial update: `price` | Giá thay đổi |
