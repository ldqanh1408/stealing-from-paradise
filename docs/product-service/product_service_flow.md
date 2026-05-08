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
  ├── Validate input (name, category_id, ít nhất 1 product_variant)
  ├── Tạo bản ghi product (status = 'inactive')
  ├── Tạo các bản ghi product_variant tương ứng
  ├── Upload ảnh → MinIO → lưu URL vào product_image
  │
  │
  └── 
    └── product.status = 'active'
        Async → Kafka topic: product.created
              → Search Service lập index document mới
```

### 1.2 Seller cập nhật giá product_variant

```
Seller PATCH /variants/:id  { price: 180000 }
  │
  ├── Lưu product_variant.price = 180000
  │
  ├── Tính lại product.status theo logic:
  │     active_count, out_of_stock_count của tất cả product_variant
  │     → Cập nhật product.status trong cùng transaction
  │
  ├── Async → Kafka topic: variant.price_updated
  │           → Search Service cập nhật document (price, min_price)
  │
  └── [KHÔNG update cart_item ngay — Lazy strategy]
      Khách hàng sẽ thấy thay đổi khi mở lại giỏ hàng
```

### 1.3 Seller cập nhật tồn kho / trạng thái product_variant

```
Seller PATCH /variants/:id  { status: 'inactive' | stock_quantity: 0 }
  │
  ├── Lưu thay đổi vào product_variant
  ├── Cập nhật Redis: SET stock:{variant_id} = stock_quantity mới
  ├── Tính lại product.status (cùng transaction)
  │
  ├── [Nếu variant hết hàng hoặc ngừng bán]
  │     KHÔNG can thiệp/update trực tiếp vào bảng cart_item (Lazy Evaluation).
  │  
  │
  └── Async → Kafka topic: variant.stock_updated
              → Search Service cập nhật stock_status trong document
```

### 1.4 Seller cập nhật thông tin sản phẩm

```
Seller PATCH /products/:id  { name, description, attributes, status }
  │
  ├── Cập nhật product fields
  ├── Nếu status = 'inactive' → ẩn khỏi listing
  │
  └── Async → Kafka topic: product.updated / product.inactive
        → Search Service cập nhật document
```

### 1.5 Seller quản lý ảnh sản phẩm

```
Seller POST /products/:id/images
  │
  ├── Upload ảnh → MinIO → lưu URL vào product_image
  └── Trả về danh sách ảnh mới nhất

Seller DELETE /products/:id/images/:image_id
  │
  └── Xóa product_image theo id
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
              { product_id, product_name, price, thumbnail_url,
                sold_count, seller_name }
```

> **Trang chủ và mọi màn hình listing đều do Search Service phục vụ.**
> Product Service chỉ được gọi khi vào trang chi tiết sản phẩm hoặc các thao tác write.

### 2.2 Trang Product Detail

```
Khách GET /products/:slug
  │
  └── Product Service (gọi trực tiếp, không qua Search Service)
        │
        ├── SELECT product WHERE slug = :slug AND status IN ('active', 'out_of_stock')
        ├── SELECT product_variant WHERE product_id = :id (tất cả variant, kể cả out_of_stock)
        └── SELECT product_image WHERE product_id = :id ORDER BY sort_order
```

---

## 3. Luồng Giỏ hàng

### 3.1 Thêm vào giỏ hàng

```
Khách POST /cart/items  { variant_id, quantity }
  │
  ├── Đọc product_variant từ Redis (hoặc DB nếu cache miss):
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
  │     price_snapshot = product_variant.price (tại thời điểm này)
  │     variant_name_snapshot = product.name + variant info
  │     variant_image_snapshot = product_variant.image_url
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
        │     Đọc data product_variant hiện tại (batch query, 1 lần từ Redis/DB)
        │     Thực hiện Lazy Evaluation:
        │       product_variant.price != cart_item.price_snapshot? (giá thay đổi / hết sale)
        │         → item.has_price_change = true, kèm giá mới
        │       product_variant.stock_quantity == 0?
        │         → item.out_of_stock = true
        │       product_variant.status != 'active'? (ngừng bán, bị ẩn)
        │         → item.is_unavailable = true
        │
        └── Trả về response cart với đầy đủ enriched data (transient states):
            (Không ghi chép xuống DB để tránh bottleneck)
```

### 3.3 Cập nhật số lượng trong giỏ hàng

```
Khách PATCH /cart/items/:variant_id  { quantity }
  │
  └── UPDATE cart_item.quantity
```

### 3.4 Xóa item khỏi giỏ hàng

```
Khách DELETE /cart/items/:variant_id
  │
  └── Xóa cart_item tương ứng
```

---

## 4. Luồng Checkout và xử lý Concurrency

### 4.1 Tổng quan 2 giai đoạn checkout (giống Shopee)

```
Giai đoạn 1: Checkout Preview
  Khách xem lại đơn hàng, chọn địa chỉ
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
        ├── Batch load tất cả product_variant liên quan
  ├── Check preview session:
  │     Nếu tồn tại `checkout_preview:{customer_id}`
  │       → Trả 409: preview_in_use
  ├── Validate từng item (Bắt chặn thay đổi do nán lại giỏ hàng):
            │     Khách bấm Checkout nhưng nán lại Cart quá lâu nên giá/stock thay đổi?
            │     Check:
            │       product_variant.status != 'active' HOẶC product_variant.stock_quantity == 0
            │         → Error: Có sản phẩm hết hàng hoặc ngừng bán.
            │       product_variant.stock_quantity < cart_item.quantity
            │         → Error: Sản phẩm không còn đủ số lượng.
            │       product_variant.price != cart_item.price_snapshot
            │         → Error: Có sản phẩm thay đổi giá hoặc hết Flash Sale.
            │
            │     Nếu BẤT KỲ check nào fail:
            │       → Trả HTTP 409 Conflict/Error kèm JSON mô tả item lỗi.
            │       FRONTEND BẮT BUỘC THÔNG BÁO CHI TIẾT LỖI CHO KHÁCH HÀNG VÀ YÊU CẦU HỌ RELOAD LẠI GIỎ HÀNG (Khách update snapshot thì mới qua bước này).
            │
            └── Nếu MỌI YÊU CẦU đều PASS (Data real-time matching perfect):
                  Tạo preview token + TTL 10 phút:
                    SET checkout_preview:{customer_id} = {preview_token} EX 600
                  Trả về preview_token + expires_at
                  Cho phép trả về Preview order với giá mới. Khách có thể đi tiếp sang bước Đặt Hàng.
```

### 4.2.1 Hủy preview (giải phóng session)

```
Khách DELETE /checkout/preview
  │
  └── DEL checkout_preview:{customer_id}
```

### 4.3 Luồng Đặt hàng — xử lý concurrency 2 lớp

```
Khách POST /checkout/place-order  { items[], payment_method, address_id, preview_token }
  │
  ├── Validate preview_token:
  │     checkout_preview:{customer_id} không tồn tại hoặc token không khớp?
  │       → Trả lỗi 409: preview_expired
  │
  ├── Re-validate tất cả items (status/stock/price) trước khi lock
  │     Sai lệch → Trả lỗi 409 + danh sách item lỗi và bắt buộc khách hàng thoát session đặt hàng này (hủy preview)
  │
  ├── [LỚP 1 — Redis Atomic, xử lý nhanh, loại bỏ request thừa sớm]
  │     Với mỗi product_variant trong order:
  │       result = Redis DECRBY stock:{variant_id} quantity
  │       result < 0?
  │         → Redis INCRBY stock:{variant_id} quantity  (hoàn trả)
  │         → Rollback toàn bộ DECRBY đã thực hiện
  │         → Trả lỗi 409: "Sản phẩm X vừa hết hàng"
  │
  ├── [LỚP 2 — DB Optimistic Lock, đảm bảo tính toàn vẹn]
  │     BEGIN TRANSACTION
  │       Với mỗi product_variant:
  │         UPDATE product_variant
  │         SET stock_quantity = stock_quantity - :qty,
  │             version = version + 1
  │         WHERE id = :variant_id
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
  ├── Order Service xử lý payment (async)
  │     → Emit Kafka: order.confirmed / order.failed
  │
  ├── [Consume order.confirmed]
  │     UPDATE stock_reservation SET status = 'confirmed'
  │
  └── [Consume order.failed]
        UPDATE stock_reservation SET status = 'released'
        UPDATE product_variant SET stock = stock + quantity
        Phục hồi trạng thái Product nếu trước đó vì đơn này mà bị đánh dấu hiển thị hết hàng
        Redis INCRBY stock:{variant_id} quantity  (hoàn trả)
        Async → Notification Service báo khách

  └── Xóa preview key: DEL checkout_preview:{customer_id}
```

### 4.4 Job cleanup reservation hết hạn

```
Scheduler chạy mỗi 1 phút:
  SELECT * FROM stock_reservation
  WHERE status = 'pending' AND expires_at < NOW()

  Với mỗi reservation hết hạn:
    BEGIN TRANSACTION
      UPDATE stock_reservation SET status = 'released'
      -- Do tồn kho đã bị trừ thẳng ở bảng product_variant lúc đặt hàng (Lớp 2),
      -- nên khi đơn bị hủy/quá hạn, ta bắt buộc phải cộng hoàn trả lại:
      UPDATE product_variant SET stock_quantity = stock_quantity + quantity
                   WHERE id = variant_id

      -- Phục hồi trạng thái Product nếu trước đó vì đơn này mà bị đánh dấu hiển thị hết hàng
      UPDATE product SET status = 'active'
                   WHERE id = (SELECT product_id FROM product_variant WHERE id = variant_id)
                   AND status = 'out_of_stock'

    COMMIT
    Redis INCRBY stock:{variant_id} quantity

### 4.5 Job đồng bộ tồn kho DB sang Redis (Self-healing & Cold Data)

Để phòng hờ trường hợp hệ thống sập gây "mất tồn kho ảo" (Microservice crash lúc vừa trừ DECRBY xong nhưng chưa lưu log vào DB), hệ thống áp dụng cơ chế Self-healing lấy Database làm Source of Truth:

```
Scheduler chạy mỗi 5 phút (Reconciliation Job):
  Batch load tất cả product_variant đang 'active' trên database.

  Với mỗi product_variant:
    Cập nhật đè cứng tồn kho thực lên: SET stock:{variant_id} = product_variant.stock_quantity
    Gán TTL vòng đời cho key: EXPIRE stock:{variant_id} 3600 (1 tiếng)
```

Kiến trúc này giúp:
- **Tự chữa lành**: Mọi sai lệch, request ma kẹt trong Redis sẽ bị "ủi phẳng" và sửa sai cứ điều đặn sau 5 phút, giữ tỉ lệ Oversell/Memory leak ở mức 0.
- **Chống tràn RAM (OOM)**: Với TTL 1 tiếng, thiết kế tự thu gọn tiết kiệm RAM cho máy chủ Redis khi tự động dọn dẹp các key `stock` của các sản phẩm ế, "nhường" tài nguyên cho Flash sale.
```

---

## 5. Luồng đồng bộ sang Search Service

```
Mỗi khi có thay đổi quan trọng trong Product Service:
  → Publish event lên Kafka

Search Service consume và cập nhật Elasticsearch:

Event: product.created / product.updated
  → Upsert document theo product_variant-first pattern
  → Mỗi product_variant của product = 1 document riêng trong ES

Event: variant.price_updated
  → Update document của variant đó: price, original_price
  → Update min_price của các variant khác trong product nếu cần

Event: variant.stock_updated
  → Update stock_status: 'in_stock' | 'out_of_stock'

Event: product.inactive
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
