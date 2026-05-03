# Search Service — API Reference

> Base path: `/api/v1` → Gateway routes to `search-service:8086`
>
> Database: Elasticsearch (product index)
>
> Kafka consumer: `product.approved` → index approved products

---

## Product Search

### GET /search/products
**Tìm kiếm sản phẩm**

**Quyền truy cập**: Public

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| q | string | Từ khóa tìm kiếm (full-text) |
| category_id | string | Lọc theo danh mục (kèm danh mục con) |
| price_min | decimal | Giá tối thiểu |
| price_max | decimal | Giá tối đa |
| in_stock | boolean | Chỉ hiện hàng còn (default: true) |
| is_flash | boolean | Chỉ hiện sản phẩm đang flash sale |
| sort | string | relevance | price_asc | price_desc | newest | sold_desc |
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Kích thước trang (default: 20, max: 100) |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "product_id": "64f3a...",
        "name": "Áo thun nam cotton",
        "description": "Áo thun cao cấp...",
        "seller_id": 10,
        "seller_name": "Shop Thời Trang",
        "category_id": "64f3b...",
        "category_name": "Áo thun",
        "price_min": 250000,
        "price_max": 300000,
        "stock_available": 100,
        "is_flash": false,
        "images": ["https://cdn.marketplace.vn/..."],
        "rating_avg": 4.5,
        "sold_count": 1000,
        "created_at": "2025-11-01T08:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "total_elements": 1,
    "total_pages": 1,
    "last": true
  }
}
```

---

## Autocomplete

### GET /search/products/suggest
**Autocomplete gợi ý tìm kiếm**

**Quyền truy cập**: Public

**Query Params:**

| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| q | string | ✓ | Chuỗi người dùng đang gõ (tối thiểu 2 ký tự) |
| size | integer | - | Số gợi ý trả về (default: 5, max: 10) |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "suggestions": [
      "áo thun nam",
      "áo thun nữ cotton",
      "áo thun oversize"
    ]
  }
}
```
