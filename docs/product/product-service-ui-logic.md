# Product Service — Logic hiển thị giao diện người dùng

## 1. Trang chủ — Card sản phẩm

Card sản phẩm ở trang chủ hiển thị ở **Product level** (không phải SKU level), nhưng các giá trị số liệu lấy từ tập hợp SKU.

### Dữ liệu hiển thị trên card

```
┌─────────────────────────────┐
│  [Thumbnail ảnh product]    │  ← product_image WHERE sku_id IS NULL
│                             │    AND sort_order = 0 (ảnh đại diện)
│  Tên sản phẩm               │  ← product.name
│                             │
│  ★★★★☆ 4.8 (1.2k đánh giá) │  ← review_summary.avg_rating
│                             │    review_summary.total_count
│  Từ 150.000đ                │  ← MIN(sku.price) WHERE sku.status = 'active'
│                             │    Hiển thị "Từ X" nếu có nhiều mức giá
│  Đã bán 3.4k                │  ← sold_count (từ Search document)
│                             │
│  [Badge: FLASH SALE -30%]   │  ← Hiển thị nếu có SKU: price < original_price
└─────────────────────────────┘
```

### Logic badge trạng thái

| Điều kiện | Badge hiển thị |
|---|---|
| `product.status = 'out_of_stock'` | "Hết hàng" (màu xám) |
| Có ít nhất 1 SKU: `price < original_price` | "SALE" hoặc phần trăm giảm |
| `product.status = 'partially_available'` | Không badge, nhưng một số biến thể sẽ disable trong Detail |
| `sold_count > threshold` | "Bán chạy" hoặc "Phổ biến" |

---

## 2. Trang Product Detail

### 2.1 Phần ảnh sản phẩm

```
Logic hiển thị ảnh:

Mặc định (chưa chọn biến thể):
  Hiển thị gallery từ product_image WHERE sku_id IS NULL
  Sắp xếp theo sort_order ASC
  Ảnh đầu tiên (sort_order nhỏ nhất) = ảnh chính lớn

Khi khách chọn biến thể (ví dụ chọn màu Đỏ):
  Kiểm tra product_image WHERE sku_id = :selected_sku_id
  → Có ảnh SKU? → Swap sang ảnh của SKU đó
  → Không có? → Giữ nguyên gallery product
```

### 2.2 Phần giá

```
Hiển thị giá theo SKU đang được chọn:

Nếu sku.original_price IS NOT NULL và sku.price < sku.original_price:
  Hiển thị: [~~original_price~~]  [price]  [-X%]
  Ví dụ:    ~~200.000đ~~  150.000đ  -25%

Nếu không có original_price:
  Hiển thị: [price]
  Ví dụ:    150.000đ

Khi chưa chọn biến thể (product level):
  Hiển thị: "Từ [min_price]đ"
  = MIN(sku.price) WHERE sku.status IN ('active', 'out_of_stock')
```

### 2.3 Phần chọn biến thể

```
Dữ liệu: tất cả SKU của product (kể cả out_of_stock)
  [{ id, variant_attributes, price, stock_quantity, status }]

Frontend group theo key của variant_attributes:

"Màu sắc":  [Đen ✓]  [Trắng]  [Đỏ - hết hàng (disabled)]
"Size":     [S]  [M ✓]  [L]  [XL]

Mỗi option:
  active + stock > 0  → Bình thường, có thể chọn
  out_of_stock        → Hiển thị nhưng disable, có thể có gạch chéo
  inactive            → Ẩn hoàn toàn (không hiển thị với khách)

Khi chọn kết hợp biến thể:
  Map sang SKU cụ thể dựa trên variant_attributes
  → Cập nhật giá hiển thị
  → Cập nhật ảnh (nếu SKU có ảnh riêng)
  → Cập nhật trạng thái nút mua
```

### 2.4 Phần thông tin sản phẩm — 2 tab

```
Tab "Chi tiết sản phẩm" ← product.attributes (JSON key-value)
  Render thành bảng:
  ┌─────────────┬──────────────────┐
  │ Chất liệu   │ 100% Cotton      │
  │ Xuất xứ     │ Việt Nam         │
  │ Phong cách  │ Casual           │
  │ Giặt ủi     │ Máy giặt ≤30°C   │
  └─────────────┴──────────────────┘

Tab "Mô tả sản phẩm" ← product.description (Rich text / HTML)
  Render HTML trực tiếp, có thể có hình ảnh, tiêu đề, danh sách
```

### 2.5 Phần điều chỉnh số lượng và nút hành động

```
Khi khách tăng/giảm số lượng:
  [−]  [3]  [+]

  Giới hạn trên = sku.stock_quantity (đọc real-time hoặc từ cache)
  Nếu quantity = stock_quantity → disable nút [+]
  Hiển thị: "Còn X sản phẩm" nếu stock_quantity <= 5 (ngưỡng cảnh báo)

Trạng thái các nút:

  sku.status = 'active' và stock_quantity > 0:
    [Thêm vào giỏ hàng]  [Mua ngay]  — cả 2 enable

  sku.status = 'out_of_stock':
    [Hết hàng] — disable
    Có thể hiển thị thêm: [Thông báo khi có hàng]

  Chưa chọn đủ biến thể:
    [Chọn phân loại hàng] — disable, nhắc chọn variant

Bấm "Thêm vào giỏ hàng":
  → Soft check tồn kho (đọc Redis/DB)
  → Nếu OK: UPSERT cart_item với price_snapshot = sku.price hiện tại
  → Hiển thị toast "Đã thêm vào giỏ hàng"

Bấm "Mua ngay":
  → Tương tự thêm vào giỏ nhưng điều hướng thẳng vào Checkout Preview
  → Không qua màn hình giỏ hàng
```

---

## 3. Trang Giỏ hàng

### 3.1 Hiển thị từng item

```
┌──────────────────────────────────────────────┐
│ [Ảnh SKU]  Áo thun nam cổ tròn              │
│            Màu: Đen | Size: M                │ ← sku_name_snapshot
│                                              │
│            ~~200.000đ~~  150.000đ            │ ← Nếu giá thay đổi:
│            ⚠ Giá đã thay đổi                │   hiển thị giá mới + warning
│                                              │
│            [−] [2] [+]              Xóa      │
└──────────────────────────────────────────────┘
```

### 3.2 Các trạng thái cảnh báo

| Trạng thái | 
|---|---|
| Runtime check: `sku.price != price_snapshot`
| Runtime check: `sku.status = 'inactive'`
| Runtime check: `sku.stock_quantity = 0`
Hiển thị cảnh báo chung: "Dữ liệu giỏ hàng đã thay đổi, vui lòng xem lại dữ liệu mới nhất"

### 3.3 Logic kiểm tra khi mở giỏ hàng

```
Mỗi khi khách mở trang giỏ hàng:
  Batch fetch data của các SKU liên quan từ Redis/DB (Lazy load).

  Với mỗi item API kiểm tra on-the-fly:
    Nếu sku.price != cart_item.price_snapshot (Lệch giá, sale kết thúc...):
      → Tính trả JSON attribute để UI hiển thị cảnh báo từ Z -> Y đ. Khách bấm cập nhật giỏ để confirm Y đ.
    
    Nếu sku.stock_quantity == 0 HOẶC status != 'active':
      → API trả về cờ disable tương ứng. UI mờ item, bỏ tick checkbox đi. (Khách có hàng lại thì lại hiện lên).

  Tổng tiền = SUM (giá có đánh dấu tick chọn checkbox x quantity).
```

---

## 4. Trang Checkout

### 4.1 Giai đoạn 1: Checkout Preview

```
Hiển thị:
  - Danh sách sản phẩm 
  - Địa chỉ giao hàng
  - Voucher / Payment Method / Total.

**Giai đoạn trước khi vào màn hình này (Bấm Checkout ở Giỏ)**:
  Khi khách bấm "Check out" sau khi nán lại trang giỏ hàng quá lâu (vd bị afk lúc flash sale diễn ra).
  API /checkout/preview bắn lên Backend. Backend check real-time:
  - Giá lệch (sku.price != price_snapshot)
  - Số lượng hụt, hết hàng
  - Inactive.
  Nếu GẶP LỖI: API bắn 400 Bad Request / 409 Conflict.
  ➔ FE chặn load Preview => Quăng popup "Dữ liệu giỏ hàng vừa thay đổi, vui lòng update", tự refresh lại giỏ. Khách phải nhấn lại Checkout khi data đồng bộ.
  *** CHƯA lock tồn kho ở bước này ***
```

### 4.2 Giai đoạn 2: Xác nhận đặt hàng

```
Khách bấm "Đặt hàng":
  → Bắt đầu lock tồn kho (tạo stock_reservation)
  → Xử lý payment

Trong thời gian chờ payment (tối đa 15 phút):
  Hiển thị màn hình chờ hoặc redirect sang cổng thanh toán
  stock_reservation.status = 'pending'
  Tồn kho đã được trừ trong Redis và DB

Payment thành công:
  → stock_reservation.status = 'confirmed'
  → Điều hướng sang trang "Đặt hàng thành công"

Payment thất bại:
  → stock_reservation.status = 'released'
  → Tồn kho hoàn trả
  → Hiển thị thông báo lỗi, cho phép thử lại hoặc chọn phương thức khác
```

---

## 5. Trang Đánh giá sản phẩm (trong Product Detail)

### 5.1 Tổng quan đánh giá

```
┌─────────────────────────────────────────┐
│  4.8 / 5          ★★★★★ (5 sao)  1.2k  │
│                   ★★★★☆ (4 sao)  180   │
│                   ★★★☆☆ (3 sao)  45    │
│                   ★★☆☆☆ (2 sao)  12    │
│                   ★☆☆☆☆ (1 sao)  8     │
└─────────────────────────────────────────┘

← Toàn bộ số liệu này đọc từ review_summary, 1 query duy nhất
   Không bao giờ COUNT(*) trực tiếp từ bảng review
```

### 5.2 Tabs bộ lọc

```
[Tất cả (1.445)] [5★ (1.200)] [4★ (180)] [3★ (45)] [2★ (12)] [1★ (8)] [Có ảnh/video (320)]

Số trong ngoặc = lấy từ review_summary (không query count mỗi lần)

Khi chọn tab:
  → Gọi GET /products/:id/reviews?rating=5 (hoặc ?has_media=true)
  → Product Service query với index (product_id, rating)
  → Phân trang: 10 review/trang
```

### 5.3 Hiển thị từng review

```
┌───────────────────────────────────────────┐
│ [Avatar] Nguyễn V***  ★★★★★              │
│          Màu: Đen | Size: M              │ ← sku.variant_attributes
│          12/04/2025                      │
│                                          │
│ Tiêu đề review                           │ ← review.title
│ Nội dung đánh giá...                     │ ← review.content
│                                          │
│ [Ảnh 1] [Ảnh 2] [Video]                 │ ← review_media (url, media_type)
│                                          │
│ 👍 Hữu ích (24)                          │ ← review.helpful_count
└───────────────────────────────────────────┘

is_anonymous = true → Ẩn tên thật, hiển thị "Người dùng ẩn danh"
```

---

## 6. Tóm tắt logic hiển thị ảnh theo context

| Context | Nguồn ảnh | Logic |
|---|---|---|
| Card trang chủ / listing | `product_image` | `sku_id IS NULL AND sort_order = MIN` |
| Product Detail — gallery mặc định | `product_image` | `sku_id IS NULL ORDER BY sort_order` |
| Product Detail — sau khi chọn màu | `product_image` | `sku_id = selected_sku_id`, fallback về product images nếu không có |
| Cart item | `cart_item.sku_image_snapshot` | Snapshot lưu lại, không phụ thuộc product còn tồn tại không |
| Review | `review_media.url` | Ảnh do khách upload, lưu trên MinIO |
