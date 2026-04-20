# Product Service — Thiết kế Database

## Tổng quan

Product Service quản lý toàn bộ vòng đời của sản phẩm trong marketplace: từ lúc seller tạo sản phẩm, qua kiểm duyệt, đến khi hiển thị cho khách hàng mua. Service này cũng tích hợp giỏ hàng (cart) để tránh phức tạp khi seller thay đổi giá SKU ảnh hưởng đến dữ liệu hiển thị cho khách.

---

## Sơ đồ quan hệ

```
CATEGORY (cây đa cấp, tự tham chiếu)
    └── PRODUCT (nhiều product thuộc 1 category)
            ├── SKU (nhiều biến thể của 1 product)
            │     └── STOCK_RESERVATION (giữ chỗ tồn kho khi checkout)
            ├── PRODUCT_IMAGE (ảnh product + ảnh theo SKU)
            ├── REVIEW (đánh giá của khách đã mua)
            │     └── REVIEW_MEDIA (ảnh/video đính kèm review)
            └── REVIEW_SUMMARY (bảng tổng hợp pre-aggregated)

CART
    └── CART_ITEM (FK sang SKU)
```

---

## Chi tiết từng bảng

### 1. CATEGORY

Danh mục sản phẩm theo cây đa cấp. Một category có thể có nhiều category con thông qua `parent_id` tự tham chiếu.

```sql
CREATE TABLE category (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID REFERENCES category(id) ON DELETE SET NULL,
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    image_url   TEXT,
    sort_order  INT DEFAULT 0,
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_category_parent ON category(parent_id);
CREATE INDEX idx_category_slug ON category(slug);
```

| Trường | Vai trò | Ghi chú |
|---|---|---|
| `parent_id` | FK tự tham chiếu để tạo cây danh mục | NULL = root category (cấp cao nhất) |
| `slug` | URL-friendly name, dùng cho SEO | Ví dụ: `ao-thun-nam` |
| `sort_order` | Thứ tự hiển thị trong menu | Số nhỏ hiển thị trước |
| `is_active` | Ẩn/hiện category | FALSE = ẩn toàn bộ product bên trong |

---

### 2. PRODUCT

Đại diện cho một sản phẩm ở cấp độ tổng quan. Một product có thể có nhiều biến thể (SKU). Khách hàng nhìn thấy Product trước, sau đó mới đi vào chọn SKU cụ thể.

```sql
CREATE TABLE product (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES category(id),
    seller_id   UUID NOT NULL,   -- ID từ Seller/User Service, không FK cứng
    name        VARCHAR(500) NOT NULL,
    slug        VARCHAR(500) NOT NULL UNIQUE,
    description TEXT,            -- Rich text / HTML — phần "Mô tả sản phẩm"
    attributes  JSONB,           -- Structured key-value — phần "Chi tiết sản phẩm"
    status      VARCHAR(50) NOT NULL DEFAULT 'draft',
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_product_category ON product(category_id);
CREATE INDEX idx_product_seller ON product(seller_id);
CREATE INDEX idx_product_status ON product(status);
CREATE INDEX idx_product_slug ON product(slug);
CREATE INDEX idx_product_attributes ON product USING GIN(attributes);
```

#### Trường `status` — các giá trị hợp lệ

| Giá trị | Ý nghĩa |
|---|---|
| `draft` | Seller đang soạn thảo, chưa gửi duyệt, chỉ seller thấy |
| `pending_review` | Đã gửi lên chờ admin/hệ thống kiểm duyệt |
| `active` | Đang bán bình thường — toàn bộ SKU còn ít nhất 1 cái active và còn hàng |
| `partially_available` | Ít nhất 1 SKU còn hàng, nhưng không phải tất cả SKU đều available |
| `out_of_stock` | Tất cả SKU đều hết hàng (`stock_quantity = 0`), vẫn hiển thị trang detail |
| `inactive` | Seller tự ẩn sản phẩm tạm thời |
| `banned` | Admin gỡ sản phẩm vi phạm chính sách |

**Logic cập nhật `status` product theo SKU (chạy trong application layer, cùng transaction với update SKU):**

```
active_count      = số SKU có status = 'active' và stock_quantity > 0
out_of_stock_count = số SKU có status = 'active' và stock_quantity = 0
inactive_count    = số SKU có status = 'inactive'

Nếu active_count > 0 và out_of_stock_count = 0  → product.status = 'active'
Nếu active_count > 0 và out_of_stock_count > 0  → product.status = 'partially_available'
Nếu active_count = 0 và out_of_stock_count > 0  → product.status = 'out_of_stock'
Nếu active_count = 0 và out_of_stock_count = 0  → product.status = 'inactive'
```

#### Trường `attributes` — ví dụ theo ngành hàng

```json
// Thời trang
{
  "material": "100% Cotton",
  "origin": "Việt Nam",
  "style": "Casual",
  "washing": "Giặt máy tối đa 30°C",
  "target": "Nam"
}

// Điện thoại
{
  "ram": "8GB",
  "storage": "256GB",
  "screen_size": "6.7 inch",
  "battery": "5000mAh",
  "os": "Android 14"
}
```

#### Trường `description`
Khác với `attributes`, `description` là **rich text / HTML** do seller soạn, nội dung tự do, phục vụ phần "Mô tả sản phẩm" trong Product Detail. Ví dụ:

```html
<h3>Giới thiệu sản phẩm</h3>
<p>Áo thun nam form regular fit, chất liệu 100% cotton cao cấp...</p>
<ul><li>Thoáng mát, thấm hút mồ hôi tốt</li></ul>
```

---

### 3. SKU (Stock Keeping Unit)

Biến thể cụ thể của product. Mỗi SKU có giá, tồn kho và bộ thuộc tính biến thể riêng. Đây là đơn vị thực sự được thêm vào giỏ hàng và mua.

```sql
CREATE TABLE sku (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    sku_code            VARCHAR(100) UNIQUE,  -- Mã SKU nội bộ của seller
    variant_name        VARCHAR(255),         -- Tên nhóm biến thể
    variant_attributes  JSONB,               -- Giá trị biến thể cụ thể
    price               DECIMAL(18,2) NOT NULL,
    original_price      DECIMAL(18,2),       -- Giá gốc để hiển thị gạch chéo
    stock_quantity      INT NOT NULL DEFAULT 0,
    status              VARCHAR(50) NOT NULL DEFAULT 'active',
    image_url           TEXT,                -- Ảnh đại diện nhanh cho SKU
    price_updated_at    TIMESTAMP,           -- Dùng để so sánh với cart snapshot
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_sku_product ON sku(product_id);
CREATE INDEX idx_sku_status ON sku(status);
CREATE INDEX idx_sku_price ON sku(price);
CREATE INDEX idx_sku_variant_attributes ON sku USING GIN(variant_attributes);
```

#### Trường `status` — các giá trị hợp lệ

| Giá trị | Ý nghĩa |
|---|---|
| `active` | Đang bán bình thường |
| `out_of_stock` | Hết hàng (`stock_quantity = 0`), vẫn hiển thị để khách biết, disable nút mua |
| `inactive` | Seller tạm ẩn biến thể này |
| `discontinued` | Ngừng bán vĩnh viễn, không khôi phục |

#### Trường `variant_name` và `variant_attributes` — ví dụ cụ thể

Một product áo thun có 4 SKU (2 màu × 2 size):

```
SKU 1:
  variant_name       = "Màu sắc, Size"
  variant_attributes = { "color": "Đen", "size": "M" }
  price              = 150000
  stock_quantity     = 10
  status             = "active"

SKU 2:
  variant_name       = "Màu sắc, Size"
  variant_attributes = { "color": "Đen", "size": "L" }
  price              = 150000
  stock_quantity     = 5
  status             = "active"

SKU 3:
  variant_name       = "Màu sắc, Size"
  variant_attributes = { "color": "Trắng", "size": "M" }
  price              = 160000
  stock_quantity     = 0
  status             = "out_of_stock"

SKU 4:
  variant_name       = "Màu sắc, Size"
  variant_attributes = { "color": "Trắng", "size": "L" }
  price              = 160000
  stock_quantity     = 8
  status             = "active"
```

Frontend nhận danh sách SKU, group theo key của `variant_attributes` để render matrix chọn biến thể. Khi khách chọn "Trắng + M" → map sang SKU 3 → `stock_quantity = 0` → disable nút mua và hiển thị "Hết hàng".

#### Trường `original_price`

Nếu `original_price` có giá trị và `price < original_price`, frontend hiển thị giạch chéo giá gốc và giá sale. Đây là cơ chế **flash sale / giảm giá thông thường**. Seller tự set 2 trường này.

> **Flash sale** là chương trình giảm giá theo thời gian — seller set `price` thấp hơn `original_price` trong khoảng thời gian nhất định. Khác với **mã giảm giá (voucher/coupon)** — voucher được áp dụng ở bước checkout và thuộc về Order/Promotion Service, không thay đổi `price` của SKU.

#### Trường `price_updated_at`

Được ghi lại mỗi khi seller thay đổi `price`. Dùng để so sánh với `cart_item.price_snapshot` khi khách mở lại giỏ hàng — nếu `price_updated_at > cart_item.price_checked_at` thì cảnh báo giá đã thay đổi.

---

### 4. PRODUCT_IMAGE

Ảnh của product và ảnh theo từng SKU (biến thể). Một product có thể có ảnh chung (gallery) và ảnh riêng cho từng màu sắc/biến thể.

```sql
CREATE TABLE product_image (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    sku_id     UUID REFERENCES sku(id) ON DELETE SET NULL,  -- NULL = ảnh chung của product
    url        TEXT NOT NULL,   -- URL trỏ đến MinIO / object storage
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_product_image_product ON product_image(product_id);
CREATE INDEX idx_product_image_sku ON product_image(sku_id);
```

| Trường | Vai trò | Ghi chú |
|---|---|---|
| `sku_id = NULL` | Ảnh chung của product | Hiển thị ở trang chủ, gallery mặc định trong Product Detail |
| `sku_id = <id>` | Ảnh riêng của biến thể | Hiển thị khi khách chọn biến thể tương ứng trong Product Detail |
| `sort_order = 0` | Ảnh đại diện (thumbnail) | Dùng cho listing page, sort_order nhỏ nhất = ảnh chính |
| `url` | URL file trên MinIO | Binary không lưu trong DB, chỉ lưu URL |

---

### 5. STOCK_RESERVATION

Giữ chỗ tồn kho khi khách bắt đầu thanh toán (sau khi bấm "Đặt hàng" ở Checkout Preview). Thay vì trừ thẳng `sku.stock_quantity`, tạo bản ghi reservation có TTL để đảm bảo tồn kho không bị oversell trong thời gian xử lý payment.

```sql
CREATE TABLE stock_reservation (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku_id     UUID NOT NULL REFERENCES sku(id),
    order_id   UUID NOT NULL,   -- ID từ Order Service, không FK cứng
    quantity   INT NOT NULL,
    status     VARCHAR(50) NOT NULL DEFAULT 'pending',
    expires_at TIMESTAMP NOT NULL,  -- Thường NOW() + 15 phút
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_reservation_sku ON stock_reservation(sku_id);
CREATE INDEX idx_reservation_order ON stock_reservation(order_id);
CREATE INDEX idx_reservation_status ON stock_reservation(status);
CREATE INDEX idx_reservation_expires ON stock_reservation(expires_at);
```

#### Trường `status` — các giá trị hợp lệ

| Giá trị | Ý nghĩa |
|---|---|
| `pending` | Đang giữ chỗ, chờ payment hoàn tất |
| `confirmed` | Payment thành công, trừ `stock_quantity` thật trong DB |
| `released` | Payment thất bại hoặc hết TTL, hoàn trả tồn kho về Redis và DB |

**Luồng xử lý:**

```
Khách bấm "Đặt hàng"
  → Tạo stock_reservation (status=pending, expires_at = NOW() + 15min)
  → Redis DECRBY stock:{sku_id} quantity   [Lớp 1]
  → UPDATE sku SET stock = stock - qty WHERE stock >= qty AND version = N  [Lớp 2]
  → rows_affected = 0? → Rollback, Redis INCR, trả lỗi hết hàng

Payment thành công
  → stock_reservation.status = 'confirmed'
  → stock_quantity đã trừ rồi, không cần làm thêm

Payment thất bại / timeout (job cleanup)
  → stock_reservation.status = 'released'
  → Redis INCR stock:{sku_id} quantity
  → UPDATE sku SET stock = stock + quantity (nếu cần sync lại DB)
```

**Job cleanup** chạy định kỳ (mỗi 1–5 phút) để release các reservation đã quá `expires_at` mà vẫn còn `status = pending`.

---

### 6. CART

Giỏ hàng của khách. Tích hợp vào Product Service để tránh phức tạp cross-service khi seller thay đổi giá SKU — thay đổi giá và đọc giỏ hàng nằm trong cùng một service.

```sql
CREATE TABLE cart (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL UNIQUE,  -- Mỗi khách chỉ có 1 cart active
    status      VARCHAR(50) NOT NULL DEFAULT 'active',
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_cart_customer ON cart(customer_id);
```

| Trường | Vai trò |
|---|---|
| `customer_id` | ID từ User/Auth Service, UNIQUE vì 1 khách = 1 cart |
| `status` | `active` (đang dùng), `merged` (sau khi guest checkout merge vào tài khoản) |

---

### 7. CART_ITEM

Từng sản phẩm trong giỏ hàng. Lưu snapshot giá tại thời điểm thêm vào để phát hiện thay đổi giá sau này.

```sql
CREATE TABLE cart_item (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id             UUID NOT NULL REFERENCES cart(id) ON DELETE CASCADE,
    sku_id              UUID NOT NULL REFERENCES sku(id),
    quantity            INT NOT NULL DEFAULT 1,

    -- Snapshot tại thời điểm thêm vào giỏ
    price_snapshot      DECIMAL(18,2) NOT NULL,
    sku_name_snapshot   VARCHAR(500),   -- Tên + variant để hiển thị kể cả SKU bị xóa
    sku_image_snapshot  TEXT,

    -- Tracking thay đổi giá
    is_price_changed    BOOLEAN DEFAULT FALSE,
    price_checked_at    TIMESTAMP DEFAULT NOW(),

    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),

    UNIQUE(cart_id, sku_id)  -- Mỗi SKU chỉ xuất hiện 1 lần trong cart
);

CREATE INDEX idx_cart_item_cart ON cart_item(cart_id);
CREATE INDEX idx_cart_item_sku ON cart_item(sku_id);
```

| Trường | Vai trò | Ghi chú |
|---|---|---|
| `price_snapshot` | Giá SKU tại lúc thêm vào giỏ | Dùng để so sánh với `sku.price` hiện tại |
| `sku_name_snapshot` | Tên + biến thể lưu lại | Hiển thị được kể cả khi SKU bị discontinued |
| `is_price_changed` | Flag cảnh báo giá đã đổi | Bật khi seller thay đổi giá hoặc SKU ngừng bán |
| `price_checked_at` | Lần cuối validate giá | So với `sku.price_updated_at` để biết cần recheck không |

**Chiến lược cập nhật giỏ hàng khi seller thay đổi SKU:**

| Loại thay đổi | Cách xử lý |
|---|---|
| Seller thay đổi giá | **Async / lazy**: không update ngay, chỉ recheck khi khách mở giỏ hàng. So sánh `sku.price_updated_at` vs `cart_item.price_checked_at` |
| SKU hết hàng | **Async**: set `is_price_changed = true` để cảnh báo khách |
| SKU bị xóa / discontinued | **Sync**: bật `is_price_changed = true` ngay, push notification cho khách |
| Bước Checkout | **Bắt buộc validate lại** giá và tồn kho real-time trước khi tạo đơn |

---

### 8. REVIEW

Đánh giá sản phẩm của khách hàng đã mua. Gắn với cả product lẫn SKU cụ thể vì mỗi biến thể có thể có trải nghiệm khác nhau.

```sql
CREATE TABLE review (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    sku_id        UUID REFERENCES sku(id) ON DELETE SET NULL,
    order_item_id UUID NOT NULL,  -- ID từ Order Service, dùng để verify "đã mua"
    customer_id   UUID NOT NULL,  -- ID từ User Service

    rating        SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title         VARCHAR(255),
    content       TEXT,
    is_anonymous  BOOLEAN DEFAULT FALSE,
    status        VARCHAR(50) NOT NULL DEFAULT 'approved',
    helpful_count INT DEFAULT 0,   -- Số người bấm "Đánh giá này hữu ích"

    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_review_product ON review(product_id);
CREATE INDEX idx_review_product_rating ON review(product_id, rating);  -- Lọc theo sao
CREATE INDEX idx_review_product_created ON review(product_id, created_at DESC);
CREATE INDEX idx_review_customer ON review(customer_id);
CREATE INDEX idx_review_status ON review(status);
```

#### Trường `status` — các giá trị hợp lệ

| Giá trị | Ý nghĩa |
|---|---|
| `approved` | Hiển thị bình thường (mặc định nếu không cần duyệt) |
| `pending` | Chờ admin duyệt (nếu có chính sách kiểm duyệt) |
| `rejected` | Không đạt tiêu chuẩn, không hiển thị |
| `hidden` | Admin ẩn tạm thời để xem xét |

---

### 9. REVIEW_MEDIA

Ảnh và video đính kèm trong review của khách. Tách riêng bảng vì 1 review có thể có nhiều file.

```sql
CREATE TABLE review_media (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id  UUID NOT NULL REFERENCES review(id) ON DELETE CASCADE,
    url        TEXT NOT NULL,         -- URL trỏ đến MinIO
    media_type VARCHAR(20) NOT NULL,  -- 'image' hoặc 'video'
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_review_media_review ON review_media(review_id);
```

---

### 10. REVIEW_SUMMARY

Bảng tổng hợp pre-aggregated để tránh COUNT/AVG toàn bộ review mỗi lần load Product Detail. Được cập nhật mỗi khi có review mới hoặc review thay đổi status.

```sql
CREATE TABLE review_summary (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id       UUID NOT NULL UNIQUE REFERENCES product(id) ON DELETE CASCADE,
    avg_rating       DECIMAL(3,2) DEFAULT 0,
    total_count      INT DEFAULT 0,
    count_5star      INT DEFAULT 0,
    count_4star      INT DEFAULT 0,
    count_3star      INT DEFAULT 0,
    count_2star      INT DEFAULT 0,
    count_1star      INT DEFAULT 0,
    count_with_media INT DEFAULT 0,  -- Số review có đính kèm ảnh/video
    updated_at       TIMESTAMP DEFAULT NOW()
);
```

**Query cho từng bộ lọc review trong Product Detail:**

```sql
-- Tất cả review
SELECT r.*, rm.url, rm.media_type
FROM review r
LEFT JOIN review_media rm ON rm.review_id = r.id
WHERE r.product_id = :product_id AND r.status = 'approved'
ORDER BY r.created_at DESC
LIMIT 10 OFFSET :offset;

-- Lọc theo số sao (ví dụ 5 sao)
SELECT * FROM review
WHERE product_id = :product_id AND rating = 5 AND status = 'approved'
ORDER BY created_at DESC;

-- Lọc có hình ảnh/video
SELECT r.* FROM review r
WHERE r.product_id = :product_id
  AND r.status = 'approved'
  AND EXISTS (SELECT 1 FROM review_media rm WHERE rm.review_id = r.id)
ORDER BY r.created_at DESC;

-- Số lượng từng loại (dùng REVIEW_SUMMARY, không cần COUNT)
SELECT total_count, count_5star, count_4star, count_3star,
       count_2star, count_1star, count_with_media, avg_rating
FROM review_summary
WHERE product_id = :product_id;
```

---

## Lưu ý về Object Storage (MinIO)

Tất cả file binary (ảnh sản phẩm, ảnh/video review của khách) đều lưu trên MinIO hoặc S3-compatible storage. Database chỉ lưu URL trỏ đến file.

```
Flow upload ảnh sản phẩm (seller):
  Seller upload → API Gateway → Media Service → MinIO
                                              → trả về URL
                              → Product Service lưu URL vào product_image.url

Flow upload ảnh review (khách):
  Khách upload → API Gateway → Media Service → MinIO
                                             → trả về URL
                             → Product Service lưu URL vào review_media.url
```
