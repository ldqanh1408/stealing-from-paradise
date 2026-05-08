# Search Service — Thiết kế Document (SKU-first + Field Collapsing)

## Tổng quan kiến trúc

Search Service sử dụng **Elasticsearch** (hoặc OpenSearch) làm search engine. Nó không thay thế Product Service mà phục vụ riêng cho các bài toán cần tốc độ cao: full-text search, filter, aggregation, scoring, và autocomplete.

**Nguyên tắc phân công:**

| Hành động | Service xử lý |
|---|---|
| Trang chủ, browse category | Search Service |
| Tìm kiếm từ khóa | Search Service |
| Bộ lọc (giá, màu, size...) | Search Service |
| Autocomplete / gợi ý | Search Service |
| Trang Product Detail | Product Service |
| Mọi thao tác write | Product Service |

---

## Lý do chọn SKU-first thay vì Product-first

### Product-first (nested) — vấn đề

```json
{
  "product_id": "prod_123",
  "name": "Áo thun nam cổ tròn",
  "skus": [
    { "color": "Đen", "size": "M", "price": 150000},
    { "color": "Trắng", "size": "L", "price": 200000}
  ]
}
```

Nếu khách filter "màu Đen + size L" — Elasticsearch với nested objects có thể trả về product này (vì có SKU Đen-M và SKU Trắng-L) ngay cả khi không có SKU nào thỏa cả 2 điều kiện đồng thời. Phải dùng `nested query` phức tạp để tránh lỗi này, và nested query chậm hơn đáng kể dưới tải cao.

Thêm vào đó, khi 1 SKU thay đổi giá hoặc hết hàng, phải reindex toàn bộ document product chứa tất cả SKU.

### SKU-first (field collapsing) — lựa chọn đúng

Mỗi SKU là 1 document độc lập. Filter chạy trên root-level fields — nhanh và chính xác. Khi hiển thị listing, dùng **field collapsing** theo `product_id` để gom về 1 card per product.

---

## Thiết kế Index Mapping

### Index name: `skus`

```json
PUT /skus
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "vietnamese_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "asciifolding"]
        }
      }
    }
  },
  "mappings": {
    "properties": {

      "sku_id":      { "type": "keyword" },
      "product_id":  { "type": "keyword" },
      "seller_id":   { "type": "keyword" },

      "product_name": {
        "type": "text",
        "analyzer": "vietnamese_analyzer",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },

      "product_slug":        { "type": "keyword" },
      "product_description": { "type": "text", "analyzer": "vietnamese_analyzer" },
      "product_attributes":  { "type": "object", "dynamic": true },

      "category_id":   { "type": "keyword" },
      "category_path": { "type": "keyword" },

      "variant_name":       { "type": "keyword" },
      "variant_attributes": { "type": "object", "dynamic": true },

      "sku_code": { "type": "keyword" },

      "price":          { "type": "double" },
      "original_price": { "type": "double" },
      "has_discount":   { "type": "boolean" },
      "discount_pct":   { "type": "integer" },

      "flash_session_id": { "type": "keyword" },

      "stock_status":   { "type": "keyword" },

      "product_status": { "type": "keyword" },
      "sku_status":     { "type": "keyword" },
      "is_active":      { "type": "boolean" },

      "thumbnail_url": { "type": "keyword", "index": false },
      "sku_image_url": { "type": "keyword", "index": false },

      "seller_name":   { "type": "text", "fields": { "keyword": { "type": "keyword" } } },

      "product_created_at": { "type": "date" },
      "sku_updated_at":     { "type": "date" },
    }
  }
}
```

---

## Ví dụ Document thực tế

Một product áo thun có 4 SKU → tạo ra 4 documents trong Elasticsearch:

```json
// Document 1: SKU Đen - M
{
  "sku_id":      "sku-001",
  "product_id":  "prod-123",
  "seller_id":   "seller-456",

  "product_name": "Áo thun nam cổ tròn Uniqlo",
  "product_slug": "ao-thun-nam-co-tron-uniqlo",
  "product_description": "Chất liệu 100% cotton cao cấp, thoáng mát...",
  "product_attributes": {
    "material": "100% Cotton",
    "origin": "Việt Nam",
    "style": "Casual"
  },

  "category_id":   "cat-ao-thun",
  "category_path": ["thoi-trang", "ao-nam", "ao-thun"],

  "variant_name": "Màu sắc, Size",
  "variant_attributes": {
    "color": "Đen",
    "size":  "M"
  },

  "sku_code": "UTM-BLK-M",

  "price":          150000,
  "original_price": 200000,
  "has_discount":   true,
  "discount_pct":   25,

  "flash_session_id": "1",

  "stock_status":   "in_stock",

  "product_status": "active",
  "sku_status":     "active",
  "is_active":      true,

  "thumbnail_url": "https://minio.example.com/products/prod-123/thumb.jpg",
  "sku_image_url": "https://minio.example.com/products/prod-123/black.jpg",

  "seller_name":   "Uniqlo Official",

  "product_created_at": "2024-01-15T00:00:00Z",
  "sku_updated_at":     "2025-04-10T08:30:00Z",
}

// Document 2: SKU Đen - L (tương tự, chỉ khác variant_attributes)
{
  "sku_id":      "sku-002",
  "product_id":  "prod-123",
  "variant_attributes": { "color": "Đen", "size": "L" },
  "price":          150000,
  "stock_status":   "in_stock",
  "sku_status":     "active",
  "is_active":      true
  // ... các trường product giống Document 1
}

// Document 3: SKU Trắng - M (hết hàng)
{
  "sku_id":      "sku-003",
  "product_id":  "prod-123",
  "variant_attributes": { "color": "Trắng", "size": "M" },
  "price":          160000,
  "stock_status":   "out_of_stock",
  "sku_status":     "out_of_stock",
  "is_active":      true   // Vẫn index để biết variant này tồn tại
  // ...
}

// Document 4: SKU Trắng - L
{
  "sku_id":      "sku-004",
  "product_id":  "prod-123",
  "variant_attributes": { "color": "Trắng", "size": "L" },
  "price":          160000,
  "stock_status":   "in_stock",
  "sku_status":     "active",
  "is_active":      true
  // ...
}
```

---

## Các Query mẫu

### Query 1: Tìm kiếm full-text + Field Collapsing

Tìm "áo thun nam", collapse về 1 card per product, đại diện là SKU giá thấp nhất còn hàng:

```json
GET /skus/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "áo thun nam",
            "fields": ["product_name^3", "product_description", "product_attributes.*"],
            "type": "best_fields",
            "fuzziness": "AUTO"
          }
        }
      ],
      "filter": [
        { "term": { "is_active": true } }
      ]
    }
  },
  "collapse": {
    "field": "product_id",
    "inner_hits": {
      "name": "cheapest_in_stock",
      "size": 1,
      "query": { "term": { "stock_status": "in_stock" } },
      "sort": [{ "price": "asc" }]
    }
  },
  "sort": [
    { "_score": "desc" },
    { "sold_count": "desc" }
  ],
  "from": 0,
  "size": 20
}
```

`collapse.inner_hits` cho phép lấy SKU đại diện tốt nhất (còn hàng, giá thấp nhất) để hiển thị giá trên card.

---

### Query 2: Browse category + Filter đa điều kiện

Lọc áo thun, giá 100k–300k, còn hàng:

```json
GET /skus/_search
{
  "query": {
    "bool": {
      "filter": [
        { "term":  { "category_id": "cat-ao-thun" } },
        { "term":  { "stock_status": "in_stock" } },
        { "term":  { "is_active": true } },
        { "range": { "price": { "gte": 100000, "lte": 300000 } } },
      ]
    }
  },
  "collapse": {
    "field": "product_id",
    "inner_hits": {
      "name": "best_sku",
      "size": 1,
      "sort": [{ "price": "asc" }]
    }
  },
  "sort": [{ "sold_count": "desc" }],
  "aggs": {
    "by_color": {
      "terms": { "field": "variant_attributes.color", "size": 20 }
    },
    "by_size": {
      "terms": { "field": "variant_attributes.size", "size": 20 }
    },
    "price_range": {
      "stats": { "field": "price" }
    },
    "by_rating": {
      "range": {
        "field": "avg_rating",
        "ranges": [
          { "from": 4 },
          { "from": 3, "to": 4 },
          { "to": 3 }
        ]
      }
    }
  },
  "size": 20
}
```

Aggregations trả về số lượng sản phẩm theo từng màu, size, khoảng giá — dùng để render bộ lọc (facets) trên sidebar mà không cần query thêm.

---

### Query 3: Autocomplete / Gợi ý từ khóa

```json
GET /skus/_search
{
  "suggest": {
    "product_suggest": {
      "prefix": "ao th",
      "completion": {
        "field": "product_name.keyword",
        "size": 10,
        "skip_duplicates": true
      }
    }
  },
  "_source": false,
  "size": 0
}
```

> Để autocomplete hiệu quả, cần thêm field `product_name.suggest` với `type: completion` trong mapping. Đây là optimization bổ sung khi cần.

---

---

## Chiến lược đồng bộ dữ liệu từ Product Service

### Khi nào Search Service cập nhật document?

**Nguồn event từ Product Service:**

```
Event: product.created
  → Index tất cả SKU của product đó (N documents mới)

Event: product.updated (name, description, attributes, status)
  → Update_by_query: WHERE product_id = :id
  → Cập nhật tất cả fields liên quan đến product

Event: sku.price_updated
  → Update document của SKU đó: price, original_price, has_discount, discount_pct

Event: sku.stock_updated
  → Update: stock_status
  → Đồng thời update product_status trong tất cả SKU của product đó nếu cần

Event: product.banned / product.inactive
  → Update is_active = false cho tất cả SKU của product
  → Hoặc delete documents nếu muốn xóa hẳn khỏi kết quả search

Event: sku.deleted
  → Delete document của SKU đó
```

**Nguồn event từ Flash Sale Service (qua Product Service):**

```
Event: flash_sale.price_sync (action=activate)
  → Product Service đã tính sẵn flash_price = sku_price * (1 - discount/100)
  → Search Service nhận event đã có đầy đủ data
  → Update document:
      - price = flash_price (giá flash sale)
      - original_price = sku_price (giá gốc)
      - has_discount = true
      - discount_pct = discount_applied
      - flash_session_id = session_id

Event: flash_sale.price_sync (action=deactivate)
  → Search Service nhận event reset price
  → Update document:
      - price = original_price
      - has_discount = false
      - discount_pct = 0
      - flash_session_id = null
```

### Partial update thay vì reindex toàn bộ

```json
// Chỉ update price của 1 SKU — nhẹ hơn nhiều so với reindex cả product
POST /skus/_update/sku-001
{
  "doc": {
    "price": 140000,
    "original_price": 200000,
    "has_discount": true,
    "discount_pct": 30,
  }
}
```

```json
// Update stock của 1 SKU
POST /skus/_update/sku-001
{
  "doc": {
    "stock_quantity": 0,
    "stock_status": "out_of_stock",
    "sku_status": "out_of_stock",
  }
}
```

---

## Pre-warm trước Flash Sale

Khi có sự kiện flash sale (hàng nghìn request đồng thời), cần đảm bảo document trong ES đã được cập nhật giá mới trước giờ mở bán:

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  PRE-WARM FLOW (T-15 phút → T+0)                                                  │
│                                                                                     │
│  T-15 phút: Cron Job kiểm tra session sắp bắt đầu                                │
│      │                                                                              │
│      ▼                                                                              │
│  ┌──────────────────────────────┐                                                    │
│  │  Flash Sale Service          │                                                    │
│  │  Publish: flash_sale.        │                                                    │
│  │    session_started           │                                                    │
│  └──────────────┬───────────────┘                                                    │
│                 ▼                                                                     │
│  ┌──────────────────────────────┐                                                    │
│  │  Product Service             │  TÍNH TOÁN:                                        │
│  │                              │    Với mỗi fs_item:                              │
│  │  Query fs_items              │      - Lấy sku_price từ product_variant           │
│  │  Query product_variants     │      - price = original_price *                   │
│  │                              │        (1 - discount_applied / 100)               │
│  │  Tính flash_price          │                                                    │
│  └──────────────┬───────────────┘                                                    │
│                 │                                                                     │
│                 ▼                                                                     │
│  ┌──────────────────────────────┐                                                    │
│  │  Search Service              │  UPDATE ES (bulk):                                │
│  │                              │    - price = price                          │
│  │  Bulk update SKUs            │    - original_price = original_price                   │
│  │                              │    - has_discount = true                           │
│  │                              │    - discount_pct                                 │
│  └──────────────┬───────────────┘                                                    │
│                 │                                                                     │
│                 ▼                                                                     │
│  ┌──────────────────────────────┐                                                    │
│  │  Redis Pre-warm              │  SET stock:{sku_id} = stock_quantity             │
│  └──────────────────────────────┘                                                    │
│                                                                                     │
│  T+0: Session ACTIVE → Buyer có thể mua ngay với giá flash sale                    │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│  POST-FLASH SALE (T_end)                                                           │
│                                                                                     │
│  T_end: Cron Job kiểm tra session kết thúc                                         │
│      │                                                                              │
│      ▼                                                                              │
│  ┌──────────────────────────────┐                                                    │
│  │  Flash Sale Service          │                                                    │
│  │  Publish: flash_sale.        │                                                    │
│  │    session_ended             │                                                    │
│  └──────────────┬───────────────┘                                                    │
│                 ▼                                                                     │
│  ┌──────────────────────────────┐                                                    │
│  │  Product Service             │  Reset price                                      │
│  └──────────────┬───────────────┘                                                    │
│                 ▼                                                                     │
│  ┌──────────────────────────────┐                                                    │
│  │  Search Service              │  UPDATE ES:                                       │
│  │                              │    - price = original_price                       │
│  │  Bulk reset SKUs             │    - has_discount = false                         │
│  └──────────────────────────────┘    - discount_pct = 0                             │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Luồng xử lý kết quả search trả về client

```
Response từ Elasticsearch (sau field collapsing):

{
  "hits": {
    "hits": [
      {
        "_source": {
          "product_id": "prod-123",
          "product_name": "Áo thun nam cổ tròn Uniqlo",
          "product_slug": "ao-thun-nam-co-tron-uniqlo",
          "category_id": "cat-ao-thun",
          "thumbnail_url": "...",
          "seller_name": "Uniqlo Official"
        },
        "inner_hits": {
          "cheapest_in_stock": {
            "hits": {
              "hits": [{
                "_source": {
                  "sku_id": "sku-001",
                  "price": 150000,
                  "original_price": 200000,
                  "has_discount": true,
                  "discount_pct": 25,
                  "stock_status": "in_stock",
                  "variant_attributes": { "color": "Đen", "size": "M" }
                }
              }]
            }
          }
        }
      }
    ]
  },
  "aggregations": {
    "by_color": { "buckets": [
      { "key": "Đen", "doc_count": 120 },
      { "key": "Trắng", "doc_count": 98 }
    ]},
    "by_size": { "buckets": [
      { "key": "M", "doc_count": 210 },
      { "key": "L", "doc_count": 185 }
    ]}
  }
}

↓ Search Service format lại trước khi trả client:

{
  "products": [
    {
      "product_id": "prod-123",
      "name": "Áo thun nam cổ tròn Uniqlo",
      "slug": "ao-thun-nam-co-tron-uniqlo",
      "thumbnail_url": "...",
      "price": 150000,           ← từ inner_hits (SKU đại diện)
      "original_price": 200000,  ← từ inner_hits
      "discount_pct": 25,
      "stock_status": "in_stock",
      "seller_name": "Uniqlo Official"
    }
  ],
  "filters": {
    "colors": [{ "value": "Đen", "count": 120 }, { "value": "Trắng", "count": 98 }],
    "sizes":  [{ "value": "M",   "count": 210 }, { "value": "L",     "count": 185 }]
  },
  "total": 450,
  "page": 1,
  "size": 20
}
```

---

## Tóm tắt lý do chọn SKU-first + Field Collapsing

| Tiêu chí | Product-first (nested) | SKU-first + Collapsing |
|---|---|---|
| Filter chính xác theo variant | Phải dùng nested query phức tạp | Root-level fields, filter đơn giản và nhanh |
| Partial update 1 SKU | Phải reindex cả document product | Update đúng 1 document SKU |
| Tránh duplicate trên listing | Tự nhiên (1 doc = 1 product) | Field collapsing theo product_id |
| Storage | Ít hơn | Nhiều hơn (duplicate product fields) |
| Query performance dưới tải cao | Thấp hơn (nested overhead) | Cao hơn |
| Độ phức tạp của mapping | Cao (nested type) | Thấp (flat document) |

Storage là đánh đổi duy nhất — nhưng với giá storage ngày nay và performance gain đáng kể, SKU-first là lựa chọn đúng cho marketplace đa ngành hàng với nhiều variant attributes.