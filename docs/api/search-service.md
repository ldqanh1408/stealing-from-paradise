# 🔍 Search Service API

**Service Name**: Search Service  
**Port**: `:8089`  
**Base URL**: `/api/v1`  
**Status**: v5.3 RTS

**Mô tả**: Elasticsearch · Full-text · Faceted filter · Aggregation

---

## 📡 Kafka Integration

### Produces (Event Publisher)
- None (Read-only service)

### Consumes (Event Subscriber)
- `product.approved` ← Admin (Index approved products)
- `product.updated` ← Product Service (Update index)
- `product.deleted` ← Product Service (Remove from index)
- `product.auto_hidden` ← Worker JOB-16 (Hide rejected products)
- `inventory.adjusted` ← Product Service (Update stock info)
- `category.updated` ← Product Service (Update category index)

---

## 🔎 Search Endpoints

### GET /search/products
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

**Response 200**:
```json
{
  "content": [
    {
      "product_id": 101,
      "name": "Áo Thun Nike Air",
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "price": 350000,
      "rating": 4.8,
      "review_count": 245,
      "stock_available": 95,
      "sold_count": 1250,
      "is_flash": false,
      "image_url": "https://cdn.marketplace.vn/products/..."
    }
  ],
  "total_elements": 1500,
  "total_pages": 75,
  "page_number": 0,
  "page_size": 20
}
```

---

### GET /search/products/suggest
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

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 2 |
| **Public Endpoints** | 2 |
| **Kafka Topics Produced** | 0 |
| **Kafka Topics Consumed** | 5 |

---

## 🔗 Integration Points

| Service | Topic | Direction | Mô tả |
|---------|-------|-----------|-------|
| **Product Service** | product.approved | ← | Index sản phẩm được duyệt |
| **Product Service** | product.updated | ← | Cập nhật thông tin sản phẩm |
| **Product Service** | product.deleted | ← | Xóa khỏi index |
| **Worker Service** | product.auto_hidden | ← | Ẩn sản phẩm bị từ chối |
| **Product Service** | inventory.adjusted | ← | Cập nhật tồn kho |

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

