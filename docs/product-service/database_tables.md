# Product Service — Thiết kế Database

## Tổng quan

Product Service quản lý toàn bộ vòng đời của sản phẩm trong marketplace: từ lúc seller tạo sản phẩm, qua kiểm duyệt, đến khi hiển thị cho khách hàng mua. Service này cũng tích hợp giỏ hàng (cart) để tránh phức tạp khi seller thay đổi giá product_variant ảnh hưởng đến dữ liệu hiển thị cho khách.

---

## Sơ đồ quan hệ

```
CATEGORY (cây đa cấp, tự tham chiếu)
    └── PRODUCT (nhiều product thuộc 1 category)
            ├── PRODUCT_VARIANT (nhiều biến thể của 1 product)
            │     └── STOCK_RESERVATION (giữ chỗ tồn kho khi checkout)
            └── PRODUCT_IMAGE (ảnh product + ảnh theo product_variant)

CART
    └── CART_ITEM (FK sang product_variant)
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

Đại diện cho một sản phẩm ở cấp độ tổng quan. Một product có thể có nhiều biến thể (product_variant). Khách hàng nhìn thấy Product trước, sau đó mới đi vào chọn product_variant cụ thể.

```sql
CREATE TABLE product (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES category(id),
    seller_id   UUID NOT NULL,   -- ID từ Seller/User Service, không FK cứng
    name        VARCHAR(500) NOT NULL,
    slug        VARCHAR(500) NOT NULL UNIQUE,
    description TEXT,            -- Rich text / HTML — phần "Mô tả sản phẩm"
    attributes  JSONB,           -- Structured key-value — phần "Chi tiết sản phẩm"
    status      VARCHAR(50) NOT NULL DEFAULT 'active',
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
| `active` | Đang bán bình thường — toàn bộ product_variant còn ít nhất 1 cái active (còn hàng) |
| `out_of_stock` | Tất cả product_variant đều hết hàng (`stock_quantity = 0`), vẫn hiển thị trang detail |
| `inactive` | Seller tự ẩn sản phẩm tạm thời |

**Logic cập nhật `status` product theo product_variant (chạy trong application layer, cùng transaction với update product_variant):**

**Product.status** phải được tính lại trong cùng transaction với bất kỳ thay đổi nào trên product_variant (cập nhật stock, thay đổi status variant, thêm/xóa product_variant).


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

### 3. PRODUCT_VARIANT (Stock Keeping Unit)

Biến thể cụ thể của product. Mỗi product_variant có giá, tồn kho và bộ thuộc tính biến thể riêng. Đây là đơn vị thực sự được thêm vào giỏ hàng và mua.

```sql
CREATE TABLE product_variant (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    variant_code        VARCHAR(100) UNIQUE,  -- Mã variant nội bộ của seller
    variant_name        VARCHAR(255),         -- Tên nhóm biến thể
    variant_attributes  JSONB,               -- Giá trị biến thể cụ thể
    price               DECIMAL(18,2) NOT NULL,
    original_price      DECIMAL(18,2),       -- Giá gốc để hiển thị gạch chéo
    stock_quantity      INT NOT NULL DEFAULT 0,
    status              VARCHAR(50) NOT NULL DEFAULT 'active',
    version             INT NOT NULL DEFAULT 1,              -- Optimistic Lock phiên bản
    image_url           TEXT,                -- Ảnh đại diện nhanh cho variant
    price_updated_at    TIMESTAMP,           -- Dùng để so sánh với cart snapshot
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_variant_product ON product_variant(product_id);
CREATE INDEX idx_variant_status ON product_variant(status);
CREATE INDEX idx_variant_price ON product_variant(price);
CREATE INDEX idx_variant_attributes ON product_variant USING GIN(variant_attributes);
```

#### Trường `status` — các giá trị hợp lệ

| Giá trị | Ý nghĩa |
|---|---|
| `active` | Đang bán bình thường và còn hàng |
| `out_of_stock` | Hết hàng (`stock_quantity = 0`), vẫn hiển thị để khách biết, disable nút mua |
| `inactive` | Seller tạm ẩn biến thể này |

#### Trường `variant_name` và `variant_attributes` — ví dụ cụ thể

Một product áo thun có 4 product_variant (2 màu × 2 size):

```
product_variant 1:
  variant_name       = "Màu sắc, Size"
  variant_attributes = { "color": "Đen", "size": "M" }
  price              = 150000
  stock_quantity     = 10
  status             = "active"

product_variant 2:
  variant_name       = "Màu sắc, Size"
  variant_attributes = { "color": "Đen", "size": "L" }
  price              = 150000
  stock_quantity     = 5
  status             = "active"

product_variant 3:
  variant_name       = "Màu sắc, Size"
  variant_attributes = { "color": "Trắng", "size": "M" }
  price              = 160000
  stock_quantity     = 0
  status             = "out_of_stock"

product_variant 4:
  variant_name       = "Màu sắc, Size"
  variant_attributes = { "color": "Trắng", "size": "L" }
  price              = 160000
  stock_quantity     = 8
  status             = "active"
```

Frontend nhận danh sách product_variant, group theo key của `variant_attributes` để render matrix chọn biến thể. Khi khách chọn "Trắng + M" → map sang product_variant 3 → `stock_quantity = 0` → disable nút mua và hiển thị "Hết hàng".

#### Trường `original_price`

Nếu `original_price` có giá trị và `price < original_price`, frontend hiển thị giạch chéo giá gốc và giá sale. Đây là cơ chế **flash sale / giảm giá thông thường**. Seller tự set 2 trường này.

> **Flash sale** là chương trình giảm giá theo thời gian — seller set `price` thấp hơn `original_price` trong khoảng thời gian nhất định. Khác với **mã giảm giá (voucher/coupon)** — voucher được áp dụng ở bước checkout và thuộc về Order/Promotion Service, không thay đổi `price` của product_variant.

#### Trường `price_updated_at`

Được ghi lại mỗi khi seller thay đổi `price`. Khi khách mở lại giỏ hàng, so sánh trực tiếp `product_variant.price` với `cart_item.price_snapshot`; nếu lệch thì cảnh báo giá đã thay đổi. `price_updated_at` dùng để audit thay đổi giá.

---

### 4. PRODUCT_IMAGE

Ảnh của product và ảnh theo từng product_variant (biến thể). Một product có thể có ảnh chung (gallery) và ảnh riêng cho từng màu sắc/biến thể.

```sql
CREATE TABLE product_image (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    variant_id UUID REFERENCES product_variant(id) ON DELETE SET NULL,  -- NULL = ảnh chung của product
    url        TEXT NOT NULL,   -- URL trỏ đến MinIO / object storage
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_product_image_product ON product_image(product_id);
CREATE INDEX idx_product_image_variant ON product_image(variant_id);
```

| Trường | Vai trò | Ghi chú |
|---|---|---|
| `variant_id = NULL` | Ảnh chung của product | Hiển thị ở trang chủ, gallery mặc định trong Product Detail |
| `variant_id = <id>` | Ảnh riêng của biến thể | Hiển thị khi khách chọn biến thể tương ứng trong Product Detail |
| `sort_order = 0` | Ảnh đại diện (thumbnail) | Dùng cho listing page, sort_order nhỏ nhất = ảnh chính |
| `url` | URL file trên MinIO | Binary không lưu trong DB, chỉ lưu URL |

---

### 5. STOCK_RESERVATION

Giữ chỗ tồn kho khi khách bắt đầu thanh toán (sau khi bấm "Đặt hàng" ở Checkout Preview). Thay vì trừ thẳng `product_variant.stock_quantity`, tạo bản ghi reservation có TTL để đảm bảo tồn kho không bị oversell trong thời gian xử lý payment.

```sql
CREATE TABLE stock_reservation (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id UUID NOT NULL REFERENCES product_variant(id),
    order_id   UUID NOT NULL,   -- ID từ Order Service, không FK cứng
    quantity   INT NOT NULL,
    status     VARCHAR(50) NOT NULL DEFAULT 'pending',
    expires_at TIMESTAMP NOT NULL,  -- Thường NOW() + 15 phút
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_reservation_variant ON stock_reservation(variant_id);
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
  → Redis DECRBY stock:{variant_id} quantity   [Lớp 1]
  → UPDATE product_variant SET stock = stock - qty WHERE stock >= qty AND version = N  [Lớp 2]
  → rows_affected = 0? → Rollback, Redis INCR, trả lỗi hết hàng

Payment thành công
  → stock_reservation.status = 'confirmed'
  → stock_quantity đã trừ rồi, không cần làm thêm

Payment thất bại / timeout (job cleanup)
  → stock_reservation.status = 'released'
  → Redis INCR stock:{variant_id} quantity
  → UPDATE product_variant SET stock = stock + quantity (nếu cần sync lại DB)
```

**Job cleanup** chạy định kỳ (mỗi 1–5 phút) để release các reservation đã quá `expires_at` mà vẫn còn `status = pending`.

---

### 6. CART

Giỏ hàng của khách. Tích hợp vào Product Service để tránh phức tạp cross-service khi seller thay đổi giá product_variant — thay đổi giá và đọc giỏ hàng nằm trong cùng một service.

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
| `status` | `active` (đang dùng) |

---

### 7. CART_ITEM

Từng sản phẩm trong giỏ hàng. Lưu snapshot giá tại thời điểm thêm vào để phát hiện thay đổi giá sau này.

```sql
CREATE TABLE cart_item (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id             UUID NOT NULL REFERENCES cart(id) ON DELETE CASCADE,
    variant_id          UUID NOT NULL REFERENCES product_variant(id),
    quantity            INT NOT NULL DEFAULT 1,

    -- Snapshot tại thời điểm thêm vào giỏ
    price_snapshot      DECIMAL(18,2) NOT NULL,
    variant_name_snapshot   VARCHAR(500),   -- Tên + variant để hiển thị kể cả variant bị xóa
    variant_image_snapshot  TEXT,

    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),

    UNIQUE(cart_id, variant_id)  -- Mỗi variant chỉ xuất hiện 1 lần trong cart
);

CREATE INDEX idx_cart_item_cart ON cart_item(cart_id);
CREATE INDEX idx_cart_item_variant ON cart_item(variant_id);
```

| Trường | Vai trò | Ghi chú |
|---|---|---|
| `price_snapshot` | Giá variant tại lúc thêm vào giỏ | Dùng để so sánh với `product_variant.price` hiện tại (Lazy calculation). Nếu khác, yêu cầu user confirm và update lại snapshot. |
| `variant_name_snapshot` | Tên + biến thể lưu lại | Hiển thị được kể cả khi variant bị discontinued |

**Chiến lược cập nhật giỏ hàng theo cơ chế Lazy Evaluation:**
Thay vì theo dõi và đẩy cập nhật từ backend vào DB mỗi khi trạng thái variant đổi, giỏ hàng sẽ tính toán real-time (on-the-fly) khi có request.

| Loại thay đổi (từ Seller) | Cách xử lý (tại Giỏ hàng) |
|---|---|
| Thay đổi giá / Flash sale hết hạn | **Pull (Get Cart)**: Tính toán so sánh `product_variant.price` và `cart_item.price_snapshot`. Nếu lệch, trả flag cảnh báo qua API để UI hiển thị. |
| Variant hết hàng | **Pull (Get Cart)**: Tính `product_variant.stock_quantity == 0`, trả flag `out_of_stock` qua API. |
| Variant bị inactive | **Pull (Get Cart)**: Tính `product_variant.status != 'active'`, trả flag `unavailable` qua API. |
| Variant có hàng lại / Active lại | **Pull (Get Cart)**: Do fetch real-time, dữ liệu trả về bình thường, tự động gỡ cảnh báo trên UI. |
| Block tại bước Checkout | **Validate Strict**: Bắt buộc kiểm tra lại toàn bộ trạng thái (giá, tồn kho, active) trước khi vào Checkout Preview. Nếu có thay đổi (do khách nán lại ở giỏ quá lâu), block lập tức, trả lỗi yêu cầu reload lại giỏ hàng chứ không cho vào preview. |

---

## Lưu ý về Object Storage (MinIO)

Tất cả file binary (ảnh sản phẩm) đều lưu trên MinIO hoặc S3-compatible storage. Database chỉ lưu URL trỏ đến file.

```
Flow upload ảnh sản phẩm (seller):
  Seller upload → API Gateway → MinIO
                              → trả về URL
                              → Product Service lưu URL vào product_image.url
```