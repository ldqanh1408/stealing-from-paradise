# Product Service — Luồng hoạt động

## Tổng quan kiến trúc

Product Service giao tiếp với các service khác theo nguyên tắc:
- **Đồng bộ (Sync / REST hoặc gRPC):** Khi cần trả kết quả ngay cho người dùng (load trang, validate checkout)
- **Bất đồng bộ (Async / Message Queue):** Khi thay đổi không cần phản hồi ngay (cập nhật giá, sync Search Service, gửi notification)

```
Client
  │
  ▼
API Gateway
  │
  ├──[Sync]──► Product Service ◄──[Sync]── Order Service (xác nhận payment)
  │                 │
  │                 ├──[Async / Kafka]──► Search Service (sync document)
  │                 ├──[Async / Kafka]──► Notification Service (cảnh báo giá, hết hàng)
  │                 └──[Read/Write]────► PostgreSQL + Redis
  │
  └──[Sync]──► Search Service (tìm kiếm, browse listing)
```

---

## 1. Luồng Seller quản lý sản phẩm

### 1.1 Tạo sản phẩm mới

```
Seller POST /products
  │
  ├── Validate input (name, category_id, ít nhất 1 SKU)
  ├── Tạo bản ghi product (status = 'draft')
  ├── Tạo các bản ghi SKU tương ứng
  ├── Upload ảnh → MinIO → lưu URL vào product_image
  │
  ├── [Nếu marketplace yêu cầu kiểm duyệt]
  │     └── product.status = 'pending_review'
  │         Async → Notification Service → báo admin có sản phẩm chờ duyệt
  │
  └── [Nếu không cần duyệt]
        └── product.status = 'active'
            Async → Kafka topic: product.created
                  → Search Service lập index document mới
```

### 1.2 Seller cập nhật giá SKU

```
Seller PATCH /skus/:id  { price: 180000 }
  │
  ├── Lưu sku.price = 180000
  ├── Ghi sku.price_updated_at = NOW()
  │
  ├── Tính lại product.status theo logic:
  │     active_count, out_of_stock_count của tất cả SKU
  │     → Cập nhật product.status trong cùng transaction
  │
  ├── Async → Kafka topic: sku.price_updated
  │           → Search Service cập nhật document (price, min_price)
  │
  └── [KHÔNG update cart_item ngay — Lazy strategy]
      Khách hàng sẽ thấy thay đổi khi mở lại giỏ hàng
```

### 1.3 Seller cập nhật tồn kho / trạng thái SKU

```
Seller PATCH /skus/:id  { status: 'inactive' | stock_quantity: 0 }
  │
  ├── Lưu thay đổi vào sku
  ├── Cập nhật Redis: SET stock:{sku_id} = stock_quantity mới
  ├── Tính lại product.status (cùng transaction)
  │
  ├── [Nếu SKU hết hàng hoặc ngừng bán]
  │     Sync (ngay lập tức):
  │       UPDATE cart_item SET is_price_changed = TRUE
  │       WHERE sku_id = :id
  │     Async → Notification Service → push cho các khách đang giữ SKU này trong cart
  │
  └── Async → Kafka topic: sku.stock_updated
              → Search Service cập nhật stock_status trong document
```

---

## 2. Luồng khách hàng xem sản phẩm

### 2.1 Trang chủ / Browse listing

```
Khách GET /products?category=ao-thun&sort=popular&page=1
  │
  └── API Gateway → Search Service (KHÔNG qua Product Service)
        │
        ├── Elasticsearch query: filter category, sort by score/sales
        ├── Field collapsing theo product_id
        │     → Mỗi product chỉ hiển thị 1 card
        │     → Đại diện là SKU giá thấp nhất, còn hàng
        │
        └── Trả về danh sách card:
              { product_id, product_name, min_price, thumbnail_url,
                avg_rating, sold_count, seller_name }
```

> **Trang chủ và mọi màn hình listing đều do Search Service phục vụ.**
> Product Service chỉ được gọi khi vào trang chi tiết sản phẩm hoặc các thao tác write.

### 2.2 Trang Product Detail

```
Khách GET /products/:slug
  │
  └── Product Service (gọi trực tiếp, không qua Search Service)
        │
        ├── SELECT product WHERE slug = :slug AND status != 'banned'
        ├── SELECT sku WHERE product_id = :id (tất cả SKU, kể cả out_of_stock)
        ├── SELECT product_image WHERE product_id = :id ORDER BY sort_order
        ├── SELECT review_summary WHERE product_id = :id
        │
        └── Trả về response đầy đủ:
              {
                product: { name, description, attributes, status },
                skus: [ { id, variant_attributes, price, original_price,
                          stock_quantity, status, image_url } ],
                images: [ { url, sku_id, sort_order } ],
                summary: { avg_rating, total_count, count_5star, ... }
              }
```

### 2.3 Load review theo bộ lọc

```
Khách GET /products/:id/reviews?rating=5&has_media=true&page=1
  │
  └── Product Service
        │
        ├── [Số đếm tabs] → SELECT từ review_summary (1 query, không COUNT)
        │
        ├── [rating=5] → SELECT WHERE product_id AND rating=5 AND status='approved'
        ├── [has_media=true] → SELECT WHERE EXISTS (SELECT 1 FROM review_media)
        ├── [tất cả] → SELECT WHERE product_id AND status='approved'
        │
        └── Kết hợp LEFT JOIN review_media để lấy ảnh/video kèm theo
```

---

## 3. Luồng Giỏ hàng

### 3.1 Thêm vào giỏ hàng

```
Khách POST /cart/items  { sku_id, quantity }
  │
  ├── Đọc sku từ Redis (hoặc DB nếu cache miss):
  │     stock_quantity, price, status, variant_attributes
  │
  ├── [Soft check — KHÔNG trừ tồn kho]
  │     stock_quantity = 0 hoặc status != 'active'?
  │       → Trả lỗi "Sản phẩm không còn hàng"
  │
  ├── quantity > stock_quantity?
  │     → Trả lỗi "Chỉ còn X sản phẩm"
  │
  ├── UPSERT cart_item:
  │     price_snapshot = sku.price (tại thời điểm này)
  │     sku_name_snapshot = product.name + variant info
  │     sku_image_snapshot = sku.image_url
  │     price_checked_at = NOW()
  │
  └── Trả về cart_item mới
```

### 3.2 Mở giỏ hàng (lazy price check)

```
Khách GET /cart
  │
  └── Product Service
        │
        ├── SELECT cart_items WHERE cart_id = :id
        │
        ├── Với mỗi cart_item:
        │     Đọc sku hiện tại (batch query, 1 lần)
        │     So sánh:
        │       sku.price != cart_item.price_snapshot?
        │         → item.price_changed = true, hiển thị cảnh báo
        │       sku.price_updated_at > cart_item.price_checked_at?
        │         → Cập nhật price_checked_at = NOW()
        │       sku.stock_quantity < cart_item.quantity?
        │         → item.stock_warning = "Chỉ còn X sản phẩm"
        │       sku.status = 'discontinued' hoặc 'inactive'?
        │         → item.unavailable = true
        │
        └── Trả về cart với enriched data (giá hiện tại, cảnh báo nếu có)
```

---

## 4. Luồng Checkout và xử lý Concurrency

### 4.1 Tổng quan 2 giai đoạn checkout (giống Shopee)

```
Giai đoạn 1: Checkout Preview
  Khách xem lại đơn hàng, chọn địa chỉ, chọn voucher
  → KHÔNG lock tồn kho
  → Validate lại số lượng (soft check) để cảnh báo nếu hết hàng

Giai đoạn 2: Đặt hàng (bấm "Đặt hàng")
  → Bắt đầu lock tồn kho (tạo stock_reservation)
  → Xử lý payment
  → Confirm hoặc release reservation
```

### 4.2 Luồng Checkout Preview

```
Khách POST /checkout/preview  { cart_item_ids[] }
  │
  └── Product Service
        │
        ├── Batch load tất cả SKU liên quan
        ├── Validate từng item:
        │     sku.stock_quantity >= cart_item.quantity?
        │       Không → warning, giảm quantity xuống còn stock_quantity
        │     sku.status = 'active'?
        │       Không → mark item unavailable
        │     sku.price != cart_item.price_snapshot?
        │       → Hiển thị giá mới, cảnh báo giá đã thay đổi
        │
        └── Trả về preview order với giá và trạng thái cập nhật nhất
            (Khách confirm thì mới tiến hành bước đặt hàng)
```

### 4.3 Luồng Đặt hàng — xử lý concurrency 2 lớp

```
Khách POST /checkout/place-order  { items[], payment_method, address_id }
  │
  ├── [LỚP 1 — Redis Atomic, xử lý nhanh, loại bỏ request thừa sớm]
  │     Với mỗi SKU trong order:
  │       result = Redis DECRBY stock:{sku_id} quantity
  │       result < 0?
  │         → Redis INCRBY stock:{sku_id} quantity  (hoàn trả)
  │         → Rollback toàn bộ DECRBY đã thực hiện
  │         → Trả lỗi 409: "Sản phẩm X vừa hết hàng"
  │
  ├── [LỚP 2 — DB Optimistic Lock, đảm bảo tính toàn vẹn]
  │     BEGIN TRANSACTION
  │       Với mỗi SKU:
  │         UPDATE sku
  │         SET stock_quantity = stock_quantity - :qty,
  │             version = version + 1
  │         WHERE id = :sku_id
  │           AND stock_quantity >= :qty
  │           AND version = :expected_version
  │         → rows_affected = 0?
  │             → ROLLBACK
  │             → Redis INCRBY (hoàn trả)
  │             → Trả lỗi 409 (conflict hoặc hết hàng thật)
  │
  │       INSERT stock_reservation (status='pending', expires_at=NOW()+15min)
  │     COMMIT
  │
  ├── Gọi Order Service [Sync] để tạo order và tiến hành payment
  │
  ├── [Payment thành công]
  │     UPDATE stock_reservation SET status = 'confirmed'
  │     Async → Kafka: order.confirmed
  │
  └── [Payment thất bại / timeout]
        UPDATE stock_reservation SET status = 'released'
        Redis INCRBY stock:{sku_id} quantity  (hoàn trả)
        Async → Kafka: order.failed → Notification Service báo khách
```

### 4.4 Job cleanup reservation hết hạn

```
Scheduler chạy mỗi 1 phút:
  SELECT * FROM stock_reservation
  WHERE status = 'pending' AND expires_at < NOW()

  Với mỗi reservation hết hạn:
    BEGIN TRANSACTION
      UPDATE stock_reservation SET status = 'released'
      -- Không cần UPDATE sku vì stock đã được trừ ở Lớp 2,
      -- cần cộng lại:
      UPDATE sku SET stock_quantity = stock_quantity + quantity
                   WHERE id = sku_id
      Redis INCRBY stock:{sku_id} quantity
    COMMIT
```

---

## 5. Luồng Flash Sale — Pre-warm Redis

Flash sale là chương trình giảm giá có thời hạn. Seller set `sku.price < sku.original_price` trong khoảng thời gian nhất định. Để tránh **cache stampede** (hàng nghìn request đồng thời miss cache và hit DB), cần pre-warm Redis trước giờ mở bán.

```
Job Pre-warm (chạy T-15 phút trước flash sale):
  SELECT id, stock_quantity FROM sku
  WHERE id IN (danh sách SKU thuộc flash sale)

  Với mỗi SKU:
    SET stock:{sku_id} = stock_quantity
    TTL = duration flash sale + 30 phút buffer

Kết thúc flash sale:
  Job cleanup release các reservation pending còn lại
  Sync lại stock_quantity: DB = source of truth
  Redis key sẽ tự expire theo TTL hoặc được overwrite
  bởi lần đọc DB tiếp theo (cache-aside pattern)
```

---

## 6. Luồng đồng bộ sang Search Service

```
Mỗi khi có thay đổi quan trọng trong Product Service:
  → Publish event lên Kafka

Search Service consume và cập nhật Elasticsearch:

Event: product.created / product.updated
  → Upsert document theo SKU-first pattern
  → Mỗi SKU của product = 1 document riêng trong ES

Event: sku.price_updated
  → Update document của SKU đó: price, original_price
  → Update min_price của các SKU khác trong product nếu cần

Event: sku.stock_updated
  → Update stock_status: 'in_stock' | 'out_of_stock'

Event: product.banned / product.inactive
  → Delete hoặc mark is_active=false tất cả document của product đó
```

---

## 7. Tóm tắt — Ai gọi Product Service vs Search Service

| Hành động | Service xử lý | Lý do |
|---|---|---|
| Trang chủ, browse, gợi ý | Search Service | Cần scoring, aggregation nhanh |
| Tìm kiếm, lọc kết quả | Search Service | Full-text, facets |
| Trang Product Detail | Product Service | Cần data đầy đủ, chính xác, real-time |
| Seller tạo/sửa sản phẩm | Product Service | Write operation |
| Thêm vào giỏ hàng | Product Service | Cần validate tồn kho real-time |
| Checkout Preview | Product Service | Validate giá và tồn kho |
| Đặt hàng | Product Service | Lock tồn kho, tạo reservation |
| Load review | Product Service | Gắn chặt với product data |
