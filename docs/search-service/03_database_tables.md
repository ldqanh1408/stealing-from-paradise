# Search Service — Database Tables

> Stack: PostgreSQL · Elasticsearch
> Cập nhật: 2026-05-05

---

## ES_PRODUCTS_INDEX (Elasticsearch)
Đánh chỉ mục sản phẩm cho tìm kiếm

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
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

## Kafka Topics (Consumer)

| Topic | Mục đích |
|-------|----------|
| `product.approved` | Index sản phẩm mới |
| `product.updated` | Đồng bộ cập nhật sản phẩm |
| `product.deleted` | Deindex sản phẩm |
| `product.auto_hidden` | Ẩn sản phẩm bị rejected |
| `account.locked` | Ẩn dữ liệu seller bị khóa |
| `inventory.adjusted` | Đồng bộ tồn kho |
| `category.updated` | Admin sửa danh mục |
