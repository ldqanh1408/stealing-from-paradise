# 🔍 Search Service API

**Port**: `:8089`  
**Mô tả**: Elasticsearch · Full-text · Faceted filter · Aggregation  
**Base URL**: `/api/v1`

---

## GET /search/products
**Tìm kiếm sản phẩm**

**Quyền truy cập**: Public

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| q | string | Từ khóa tìm kiếm (full-text) |
| category_id | string | Lọc theo danh mục (kèm danh mục con) |
| price_min | decimal | Giá tối thiểu |
| price_max | decimal | Giá tối đa |
| in_stock | boolean | Chỉ hiện hàng còn (default: true) |
| is_flash | boolean | Chỉ hiện sản phẩm đang flash sale |
| sort | string | relevance \| price_asc \| price_desc \| newest \| sold_desc |
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Kích thước trang (default: 20, max: 100) |

**Ví dụ Query**:
```
GET /search/products?q=áo thun&category_id=507f1f77bcf86cd799439011&price_min=100000&price_max=500000&in_stock=true&sort=price_asc&page=0&size=20
```

**Response 200**:
```json
{
  "total_results": 156,
  "page": 0,
  "size": 20,
  "total_pages": 8,
  "products": [
    {
      "product_id": "507f1f77bcf86cd799439012",
      "name": "Áo Thun Nike Air Nam",
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "category_id": "507f1f77bcf86cd799439011",
      "category_name": "Áo Thun Nam",
      "price_min": 250000,
      "price_max": 450000,
      "images": [
        "https://cdn.marketplace.vn/products-media/products/5/101/uuid-front.jpg"
      ],
      "stock_available": 95,
      "is_flash": true,
      "created_at": "2026-04-01T08:00:00Z"
    }
  ]
}
```

---

## GET /search/products/suggest
**Autocomplete gợi ý tìm kiếm**

**Quyền truy cập**: Public

**Query Params**:
| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| q | string | ✓ | Chuỗi người dùng đang gõ (tối thiểu 2 ký tự) |
| size | integer | - | Số gợi ý trả về (default: 5, max: 10) |

**Response 200**:
```json
{
  "suggestions": [
    "áo thun nam",
    "áo thun nữ cotton",
    "áo thun oversize"
  ]
}
```

---

## 📊 Summary

| Endpoint | Method | Auth |
|----------|--------|------|
| /search/products | GET | Public |
| /search/products/suggest | GET | Public |

**Kafka Topics consumed by Search Service**:
- `product.approved` — Index sản phẩm mới
- `product.updated` — Đồng bộ cập nhật sản phẩm
- `product.deleted` — Deindex sản phẩm
- `product.auto_hidden` — Ẩn sản phẩm bị rejected
- `account.locked` — Ẩn dữ liệu seller bị khóa
- `inventory.adjusted` — Đồng bộ tồn kho
- `category.updated` — Admin sửa danh mục

---

**Phiên bản:** v5.4  
**Cập nhật:** 2026-04-30  
**Cập nhật:** 2026-04-15
