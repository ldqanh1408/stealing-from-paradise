# 📱 Marketplace Platform

**Tài liệu Nghiệp Vụ thuần túy • Phiên bản v5.5 • Cập nhật: Return To Sender (RTS) • Thanh toán: Stripe • 17 Cronjobs (v5.0 — Distributed per Service)**

> **v5.0:** Cronjobs được chạy trong service tương ứng (identity-service, flashsale-service, product-service, order-service, payment-service, notification-service). Không có worker-service trung tâm. Xem chi tiết tại [05_OPERATIONS.md](05_OPERATIONS.md).

## Thống Kê Tổng Quan

- **7** Luồng nghiệp vụ
- **4** Chính sách cột lại
- **17** Cronjobs
- **3** Vai trò người dùng

---

## 📊 Tổng Quan Hệ Thống

Marketplace Platform là sàn thương mại điện tử đa nhà bán (multi-vendor). Buyer mua hàng, Seller bán hàng, Admin quản lý vận hành. Thanh toán xử lý qua **Stripe** (không dùng VNPay).

### ✔️ Hoàn thiện nghiệp vụ cột lại:
- Xác thực
- Vòng đời đơn hàng 8 trạng thái
- Thanh toán Stripe multi-vendor
- Hoàn tiền (Buyer + RTS từ đơng)
- Flash Sale concurrency cao
- Product lifecycle
- 17 Cronjobs
- Outbox + DLQ
- Data Retention

---

## 🔐 Vai Trò & Quyền Hạn

### 👤 BUYER
- Đăng ký tài khoản, xác minh phone + email
- Duyệt sản phẩm, thêm vào giỏ hàng
- Checkout, thanh toán qua Stripe
- Tham gia Flash Sale (trust_score ≥ 30)
- Xác nhận nhận hàng hoặc mở yêu cầu hoàn tiền (≤ 7 ngày sau DELIVERED)
- Sử dụng và tích lũy điểm Loyalty
- Khiếu nại Trust Score (tối đa 3 lần/năm)

### 💼 SELLER
- Phải hoàn thành Stripe KYC trước khi bán hàng
- Đăng sản phẩm (chỉ Admin duyệt)
- Quản lý tồn kho, cập nhật tracking đơn hàng
- Đăng ký sản phẩm vào Flash Sale (Admin duyệt)
- Xác nhận hàng hoàn về (RTS) để không cần Admin duyệt refund
- Nhận tiền qua Stripe Transfer sau khi đơn DELIVERED
- Bị tạm dừng đăng bị nếu vi phạm 3 lần/30 ngày

### 🔧 ADMIN
- Duyệt / từ chối sản phẩm (ghi lý do)
- Duyệt / từ chối yêu cầu hoàn tiền Buyer
- Duyệt / từ chối Flash Sale items
- Khóa / mở khóa tài khoản (có/không thời hạn)
- Điều chỉnh Trust Score thủ công
- Xử lý Appeal của user
- Chỉnh cấu hình delta Trust Score đơng
- Giám sát và retry các sự kiện thất bại (DLQ)

**Lưu:** Một user có thể có nhiều role. Ví dụ: một người vừa là BUYER vừa là SELLER. Đăng ký role SELLER yêu cầu hoàn thành Stripe KYC (xác minh danh tính kinh doanh).

---

## 🔐 Luồng: Xác Thực & Quản Lý Tài Khoản

### Đăng Ký & Đăng Nhập

```
Người dùng đăng ký → xác minh email → đăng nhập
 ↓
 → Phiên đăng nhập: Access Token (15 phút) + Refresh Token (7 ngày, tự gia hạn mỗi lần dùng)
 ↓
 → Khi token hết hạn → dùng Refresh Token để lấy Access Token mới
 ↓
 → Đăng xuất → token bị thu hồi ngay lập tức (không chỉ hết hạn)
```

### Khóa Tài Khoản (Admin)

```
Admin khóa tài khoản user:
 → Tài khoản chuyển sang trạng thái LOCKED
 → Toàn bộ phiên đăng nhập hiện tại bị thu hồi ngay
 → Mọi request sau đó bị từ chối (không cần chờ token hết hạn)
 → Ghi vào lịch sử khóa/mở (audit trail)
 
Admin mở khóa:
 → Tài khoản chuyển về ACTIVE
 → User đăng nhập lại bình thường
```

### Đăng Ký Vai Trò Seller

```
User (đang là BUYER) muốn trở thành SELLER:
 → Bắt đầu quy trình Stripe KYC (xác minh danh tính kinh doanh)
 → Stripe cấp link onboarding (hết hạn sau 24 giờ → JOB-15 dọn link cũ)
 → Sau khi hoàn thành KYC → Stripe xác nhận → User có thêm role SELLER
 → Bắt đầu đăng sản phẩm được
```

---

## 📦 Luồng: Vòng Đời Sản Phẩm

**Trạng thái sản phẩm:** PENDING → (Admin duyệt →) APPROVED hoặc REJECTED → (90 ngày không sửa) → Soft-deleted → Hard-deleted

```
Seller đăng sản phẩm → trạng thái: PENDING (chờ duyệt)
 ↓
 → Admin DUYỆT → APPROVED
 │   → Sản phẩm hiển thị trên kết quả tìm kiếm
 │   → Trust Score Seller được cộng điểm
 ↓
 → Admin TỪ CHỐI → REJECTED (có ghi lý do)
 │   → Thông báo đến Seller (lý do + thời hạn sửa)
 │   → Trust Score Seller bị trừ điểm (lần đầu vs tái phạm)
 │   → Seller có 90 ngày để sửa và gửi lại
 │   → JOB-16 (hàng ngày 03:00): Sản phẩm bị REJECTED và không sửa trong 90 ngày
 │     → đánh dấu soft-deleted
 ↓
 → JOB-10 (Chủ nhật 03:00): Hard delete sản phẩm ở soft-deleted
     Điều kiện bắt buộc: không có đơn hàng đang chờ xử lý liên quan
```

**Tìm kiếm:** Sản phẩm chỉ xuất hiện trong kết quả tìm kiếm khi ở trạng thái APPROVED. Khi Seller bị tạm dừng đăng bị, sản phẩm bị ẩn khỏi tìm kiếm từ đơng.

---

## 🛒 Luồng: Vòng Đời Đơn Hàng

**Trạng thái đơn hàng:**
- Chính: PENDING → PAID → SHIPPING → DELIVERED
- Ngoại lệ: RETURNED, REFUNDED, PARTIALLY_REFUNDED, CANCELLED

```
Buyer checkout → đơn hàng cha (PARENT_ORDER) tạo ra
 Đơn con (1 đơn per Seller) → tất cả bắt đầu ở trạng thái PENDING
 ↓
 → Thanh toán thành công → PAID
 │   → Hàng hóa bị "khóa kho" (không bán cho người khác)
 │   → Điểm Loyalty PENDING được ghi nhận (chỉ xác nhận)
 │   → Seller nhận thông báo có đơn mới
 ↓
 → PAID → Seller cập nhật mã vận đơn → SHIPPING
 │   → Buyer nhận thông báo + mã để theo dõi đơn hàng
 │   → Đơng hộ theo dõi giao hàng bắt đầu chạy (3 ngày)
 │   → JOB-13b: Nếu giao hàng chậm quá 3 lần/tháng → trừ Trust Score Seller
 ↓
 → SHIPPING → Buyer xác nhận nhận hàng → DELIVERED
 │   → Điểm Loyalty được CONFIRMED (vào tài khoản chính thức)
 │   → Trust Score Seller được cộng điểm
 │   → Tiền chuyển cho Seller qua Stripe
 ↓
 → SHIPPING → JOB-22 (mỗi ngày 02:00): đơn > 7 ngày chưa xác nhận
 │   → Tự động chuyển sang DELIVERED (trình Buyer "quên" xác nhận)
 │   → ⚠️ NGOẠI LỆ: đơn ở có Hàng Hoàn (RTS) không bị tự động DELIVERED
 ↓
 → SHIPPING → Seller xác nhận hàng bị hoàn về (RTS) → RETURNED
 │   → Xem luồng Hàng Hoàn (RTS)
 ↓
 → DELIVERED → Buyer mở yêu cầu hoàn tiền (trong 7 ngày)
 │   → Xem luồng Hoàn Tiền
 ↓
 → PENDING hoặc PAID → Hủy đơn
     Ai có thể hủy:
     - BUYER: hủy tự do khi PENDING hoặc PAID (trước khi Seller giao hàng)
     - SELLER: hủy khi PENDING hoặc PAID → trừ Trust Score Seller nặng
     - HỆ THỐNG: JOB-13 tự hủy đơn quá hạn thanh toán
       (đơn thường: 30 phút • đơn Flash Sale: 10 phút)
     
     Khi hủy: kho được mở khóa, điểm Loyalty PENDING bị hủy theo
```

---

## 💳 Luồng: Thanh Toán Stripe Multi-Vendor

Hệ thống sử dụng **Stripe** làm công cụ thanh toán duy nhất. Mô hình multi-vendor: Buyer thanh toán 1 lần, tiền tự động chia cho từng Seller tương ứng sau khi đơn hoàn thành.

### Luồng Stripe Transfer chuẩn:

```
order.delivered
 ↓
Payment Service nhận event ở giao hàng
 ↓
Gọi Stripe API: stripe.transfers.create
 ↓
Ghi giao dịch vào bảng SELLER_TRANSFERS
 ↓
Cập nhật trạng thái transfer = SUCCESS / FAILED
 ↓
Thông báo cho Seller về số tiền vừa nhận được
```

Luồng này chỉ chạy khi đơn hàng ở trạng thái DELIVERED hợp lệ. Nếu đơn chuyển sang RETURNED theo RTS thì đi theo luồng hoàn tiền tự động, không tạo transfer cho Seller.

### Luồng thanh toán tổng quát

```
Buyer checkout (có thể nhiều Seller trong 1 giỏ hàng):
 ↓
 → Buyer thanh toán: 1 giao dịch duy nhất qua Stripe
 │   → Stripe tự động thu phí sàn (application fee)
 │   → Phần còn lại chia cho từng Seller tương ứng
 │   → Mỗi Seller nhận tiền vào tài khoản Stripe của họ
 ↓
 → Thanh toán thành công → đơn hàng chuyển sang PAID
 ↓
 → Thanh toán thất bại → thông báo Buyer (có deeplink thử lại)
 ↓
 → Onboarding Seller (KYC):
 │   Seller nhận link xác minh Stripe (hết hạn 24 giờ)
 │   Sau xác minh → Stripe xác nhận → Seller có thể nhận tiền
 ↓
 → Hoàn tiền (khi cần):
     - Buyer refund (Admin duyệt): Stripe Refund API
     - Hàng Hoàn RTS: Refund tự động (không cần Admin duyệt)
```

### Cơ chế Hoàn Tiền khi Refund

Khi Buyer được hoàn tiền một phần: Stripe thực hiện **Transfer Reversal** để đảo ngược phần tiền đã chuyển cho Seller, trả lại vào tài khoản Buyer qua Stripe. Mỗi Seller bị hoàn tiền độc lập, không nh hưởng Seller khác.

---

## 💰 Luộng: Hoàn Tiền Buyer (Refund)

### ⚠️ Điều kiện để Buyer mở yêu cầu hoàn tiền:
- Đơn hàng đang ở trạng thái DELIVERED
- Trong vòng 7 ngày kể từ DELIVERED
- Tài khoản đã xác minh đầy đủ (phone + email)
- Chưa có yêu cầu hoàn tiền đang mở cho đơn này

### Luồng hoàn tiền

```
Buyer gửi yêu cầu hoàn tiền + upload bằng chứng nh
 → Yêu cầu ở trạng thái PENDING
 → Thông báo đến Seller (chuẩn bị giải trình nếu cần)
 ↓
Admin xem xét yêu cầu + bằng chứng:
 ↓
 → Admin DUYỆT:
 │   → Nhập thông tin hoàn (amount, tracking number nếu có)
 │   → Stripe thực hiện hoàn tiền về tài khoản Buyer
 │   → Nếu tracking number được cung cấp:
 │       → Lưu vào REFUND_ITEMS.tracking_number (NEW v5.3)
 │       → Ghi vào REFUND_ITEMS.return_evidence (audit trail)
 │       → Thông báo Buyer kèm mã vận đơn hoàn
 │   → Nếu lỗi do Seller gây ra → trừ Trust Score Seller
 │   → Đơn hàng: REFUNDED (toàn phần) hoặc PARTIALLY_REFUNDED
 │   → Điểm Loyalty ở dùng được hoàn lại
 │   → Thông báo Buyer (tiền đang về, tracking số nếu có)
 ↓
 → Admin TỪ CHỐI:
     → Yêu cầu bị REJECTED, ghi lý do
     → Nếu bằng chứng giả → trừ Trust Score Buyer nặng
     → Buyer nhận thông báo lý do + link khiếu nại
```

**NEW v5.3: Tracking Number Requirement**

Khi Admin duyệt refund, có thể cung cấp `tracking_number` (mã vận đơn hoàn) nếu có:

| Tình Huống | Tracking Number | Mô Tả |
|-----------|-----------------|-------|
| **Hoàn tiền do lỗi giao hàng** | ✓ Bắt buộc | Mã vận đơn hoàn được ghi nhận để audit |
| **Hoàn tiền do lỗi sản phẩm** | ✓ Nên cung cấp | Nếu hàng được hoàn lại |
| **Hoàn tiền không liên quan vận chuyển** | ✗ Optional | Ví dụ: refund do Admin phát hiện lỗi |

**Lợi Ích**:
- ✅ Audit trail đầy đủ cho tracking/vận chuyển
- ✅ Buyer nhận được mã hoàn để theo dõi
- ✅ Admin có bằng chứng xử lý refund
- ✅ Tuân thủ pháp lý (invoice/receipt tracking)

### Hoàn Tiền Toàn Phần vs Một Phần

| Loại | Phạm vi | Kết quả đơn hàng |
|------|--------|-----------------|
| **Full Refund** | Toàn bộ đơn cha (tất cả Seller) | REFUNDED |
| **Partial Refund** | Một số sản phẩm có thể | PARTIALLY_REFUNDED |

---

## 🔄 Luồng: Hàng Hoàn Về Seller — Return To Sender (RTS) [NEW v5.3]

### 🚨 Vấn đề cũ (đã giải quyết):
Khi shipper hoàn hàng về Seller (gọi 3 lần không nghe / địa chỉ sai), hệ thống cũ tự động xác nhận "đã giao" sau 7 ngày nếu Buyer không phản hồi → Seller vẫn có hàng vẫn có tiền. Luồng RTS giải quyết triệt để vấn đề này.

### Luồng RTS chi tiết

```
Đơn vận chuyển bị hoàn về cho Seller (đơn đang ở SHIPPING)
 ↓
Seller xác nhận hàng ở nhận lại:
 → Cung cấp bằng chứng (nh, bắt buộc ít nhất 1 nh)
 → Có thể kèm mã vận đơn hoàn, ghi chú
 ↓
Hệ thống xử lý ngay lập tức (tất cả trong 1 thao tác):
 (1) Đơn hàng chuyển sang trạng thái RETURNED
 (2) Yêu cầu hoàn tiền tạo tự động (toàn phần, do Seller khởi tạo)
 (3) Tồn kho được cộng lại ngay
 ↓
Hoàn tiền tự động (KHÔNG cần Admin duyệt):
 → Stripe thực hiện hoàn tiền về tài khoản Buyer ngay
 → Không mất thời gian chờ Admin xử lý
 ↓
Thông báo:
 → Buyer: "Seller đã nhận lại hàng hoàn. Tiền đang được hoàn về..."
 → Seller: "Xác nhận thành công. Tồn kho đã được cộng lại."
 ↓
⚠️ JOB-22 (tự động DELIVERED sau 7 ngày) KHÔNG áp dụng cho đơn RTS
    → Đơn RTS sẽ không bị nhầm chuyển sang DELIVERED
```

### ✔️ Điểm mạnh chủ chốt của RTS:
Seller chỉ đơng xác nhận và chịu trách nhiệm → hệ thống tính toán tin tưởng → hoàn tiền tự động ngay, không cần Admin làm trung gian. Bằng chứng nh lưu vĩnh viễn cho mục đích pháp lý.

---

## ⚡ Luồng: Flash Sale

Flash Sale là sự kiện bán hàng giảm giá trong thời gian ngắn, lưng truy cập các cao đơng thời. Hệ thống đảm bảo không có tình trạng bán quá số lượng (oversell).

### Trạng thái Flash Sale Session

**UPCOMING → (khi giờ) → ACTIVE → (hết giờ) → ENDED**

### Giai đoạn chuẩn bị (Admin + Seller):

```
Admin tạo phiên Flash Sale (thời gian bắt đầu, kết thúc) → UPCOMING
Seller đăng ký sản phẩm + giá Flash Sale + số lượng + giới hạn/người → PENDING
Admin duyệt → số lượng Flash Sale bị "khóa" khi kho thông ngay
Seller nhận thông báo được duyệt/từ chối

Trước khi bắt đầu:
 JOB-02 (mỗi 1 phút): 15 phút trước giờ mở → nhắc Buyer ở đăng ký nhắc nhở

Khi Flash Sale bắt đầu (JOB-01 mỗi 1 phút):
 → Kho Flash Sale được nạp lên bộ nhớ đầm tốc độ cao

Buyer mua hàng Flash Sale (đơng thời hàng ngàn người):
 Mỗi lần đặt hàng:
 1. Kiểm tra còn hàng → nếu hết: báo ngay "Hết hàng"
 2. Kiểm tra giới hạn per-user → nếu vượt: từ chối
 3. Thành công → tạo đơn Flash Sale (timeout 10 phút thay vì 30 phút)

Khi Flash Sale kết thúc (JOB-01):
 → Số lượng chưa bán được từ hoàn lại vào kho thông của Seller
 → Thông báo Seller phiên kết thúc

JOB-21 (mỗi 5 phút): Đối sánh kho để phát hiện và sửa bất đơng bộ
```

### Quy tắc tham gia Flash Sale

| Đối tượng | Điều kiện |
|----------|----------|
| Buyer tham gia | Trust Score ≥ 30 • Tài khoản ACTIVE • Xác minh đầy đủ |
| Seller đăng ký sản phẩm | Stripe KYC hoàn tất • Sản phẩm đang APPROVED • Số lượng Flash ≤ số lượng tồn trong kho |
| Timeout đơn hàng | **10 phút** (so với 30 phút đơn thường) |

---

## 💳 Chính Sách: Hoàn Tiền

### Điều kiện mở yêu cầu hoàn tiền (Buyer)

| Điều kiện | Yêu cầu |
|----------|---------|
| Trạng thái đơn hàng | Phải là DELIVERED |
| Thời gian | Trong vòng 7 ngày kể từ khi đơn DELIVERED |
| Xác minh tài khoản | Phải có đầy đủ phone lẫn email |
| Không trùng lặp | Không có yêu cầu hoàn tiền đang mở (PENDING/SUCCESS) cho đơn này |

### Admin Duyệt Hoàn Tiền (NEW v5.3)

Khi Admin duyệt refund, **bắt buộc nhập tracking number nếu liên quan đến hàng hoàn**:

| Trường | Bắt Buộc? | Mô Tả |
|--------|----------|-------|
| `admin_note` | ✓ Bắt buộc | Lý do Admin can thiệp (ví dụ: "Lỗi giao hàng", "Confirm RTS") |
| `adjust_amount` | ✗ Optional | Điều chỉnh số tiền hoàn (nếu khác original) |
| `caused_by` | ✗ Optional | SELLER \| BUYER - để tính trừ Trust Score |
| `tracking_number` | ✓ Khi có | Mã vận đơn hoàn (nếu hàng được hoàn về hoặc cần track) |

**Quy Tắc Nhập Tracking Number**:

| Tình Huống | Tracking Number | Ví Dụ |
|-----------|-----------------|-------|
| **Hoàn tiền do giao hàng thất bại** | ✓ **BẮTBUỘC** | VT123456 (mã vận đơn hoàn từ shipper) |
| **Hoàn tiền do sản phẩm lỗi + hàng hoàn** | ✓ **NÊN CÓ** | VT123456 (shipper lấy lại hàng) |
| **Hoàn tiền RTS (Return To Sender)** | ✓ **CÓ** | Mã vận đơn hoàn do Seller cung cấp |
| **Hoàn tiền do Admin phát hiện lỗi** | ✗ Optional | Nếu không liên quan vận chuyển |
| **Hoàn tiền do tranh chấp Buyer/Seller** | ✗ Optional | Chỉ nhập nếu hàng cần hoàn |

**Lưu Trữ & Audit**:
- Tracking number lưu vào `REFUND_ITEMS.tracking_number` (NEW v5.3)
- Tạo audit trail: `REFUND_ITEMS.return_evidence`
- Thông báo Buyer kèm mã hoàn để theo dõi vận chuyển
- Tuân thủ pháp lý: đầy đủ invoice/receipt tracking

### Luồng RTS — Không cần Admin duyệt

Khi Seller xác nhận hàng bị hoàn về (Return To Sender): hệ thống tự động hoàn tiền Buyer qua Stripe mà không cần Admin duyệt. Seller ở chịu trách nhiệm bằng cách upload bằng chứng nh. Bằng chứng được lưu vĩnh viễn cho mục đích pháp lý.

---

## ⚡ Chính Sách: Flash Sale

| Quy tắc | Nội dung |
|--------|---------|
| Điều kiện Buyer tham gia | Trust Score ≥ 30 • Tài khoản ACTIVE • Xác minh đầy đủ |
| Điều kiện Seller đăng ký | Stripe KYC hoàn tất • Sản phẩm APPROVED • Số lượng Flash ≤ tồn kho thực tế |
| Timeout đơn Flash Sale | **10 phút** (so với 30 phút đơn thường) |
| Đối sánh kho định kỳ | JOB-21 mỗi 5 phút: đảm bảo không có sai lệch kho |
| Hết hàng | Từ chối ngay lập tức, không phải chờ |
| Vượt giới hạn per-user | Từ chối ngay lập tức |

---

## 📊 Chính Sách: Lưu Trữ Dữ Liệu

| Dữ liệu | Thời gian giữ | Job xử lý |
|--------|-------------|----------|
| Người dùng, đơn hàng, Giao dịch thanh toán, Hoàn tiền | **Vĩnh viễn** | Không xóa |
| Nh bằng chứng hoàn tiền / bằng chứng Appeal | **Vĩnh viễn** | Lưu trữ pháp lý |
| Sự kiện hệ thống ở xử lý | 7 ngày | JOB-05 |
| Sự kiện hệ thống thất bại | 3 ngày | JOB-05 |
| Sự kiện ở giải quyết / chết hạn | 30-90 ngày | JOB-06 |
| Phiên Flash Sale (sau khi kết thúc) | 365 ngày | JOB-08 |
| Sản phẩm Flash Sale (ở duyệt) | 180 ngày | JOB-08 |
| Sản phẩm Flash Sale (bị từ chối / hủy) | 30 ngày | JOB-08 |
| Nhắc nhở Flash Sale | Xóa ngay sau khi phiên kết thúc | JOB-08 |
| Thông báo trong hệ thống | 90 ngày | Tự động (TTL Index) |
| Giỏ hàng không hoạt động | 90 ngày | JOB-07 |
| Sản phẩm bị xóa mềm | Xóa khi không còn đơn hàng liên quan | JOB-10 (Chủ nhật 03:00) |

---

## 🔐 Chính Sách: Bảo Mật & Phân Quyền

### Phiên Đăng Nhập

- Phiên ngắn: tự hết hạn sau **15 phút**
- Gia hạn tự động: mỗi lần dùng Refresh Token (7 ngày), token có hiệu ngay
- Khóa tài khoản → tất cả phiên bị thu hồi ngay lập tức

### Phân Quyền

- **BUYER:** Mua hàng, hoàn tiền, điểm, giỏ hàng
- **SELLER:** Sản phẩm, đơn hàng Seller, RTS, Flash Sale
- **ADMIN:** Tất cả tính năng quản trị, duyệt/từ chối, khóa/mở
- 1 user có thể có nhiều role cùng lúc

### Nh & Tài Liệu

- Không bao giờ expose URL trực tiếp ra bên ngoài
- Truy cập qua **link tạm thời (15 phút)**
- Nh bằng chứng hoàn tiền / appeal: giữ vĩnh viễn (pháp lý)
- Avatar, nh sản phẩm: truy cập qua link tạm thời

---

## 🤖 Cronjobs — 17 Jobs Tự động (v5.0 — Distributed per Service)

> **Mỗi job chạy trong service sở hữu primary data.** Không có worker-service trung tâm. Xem chi tiết tại [05_OPERATIONS.md](05_OPERATIONS.md).

### ⚡ Thời gian thực (1-5 phút)

| Job | Service | Mô Tả |
|-----|---------|--------|
| JOB-01 | flashsale-service | Vòng đời Flash Sale: UPCOMING→ACTIVE→ENDED |
| JOB-02 | flashsale-service | Nhắc nhở Buyer 15 phút trước khi bắt đầu |
| JOB-04 | payment-service | Outbox event publisher (mỗi 10 giây) |
| JOB-13 | order-service | Auto-cancel đơn quá hạn (30p thường, 10p FS) |
| JOB-21 | flashsale-service | Reconciliation tồn kho Redis vs DB |

### 🌅 Hàng ngày

| Job | Service | Mô Tả |
|-----|---------|--------|
| JOB-07 | product-service | Dọn cart không hoạt động (90 ngày) |
| JOB-08 | flashsale-service | Dọn dữ liệu Flash Sale theo retention |
| JOB-12 | payment-service | Dọn ShedLock stale entries |
| JOB-15 | payment-service | Nullify Stripe onboarding URL (>24h) |
| JOB-16 | product-service | Soft-delete sản phẩm REJECTED không sửa (90 ngày) |
| JOB-17 | identity-service | Auto-lock/unlock tài khoản theo Trust Score |
| JOB-22 | order-service | Auto-delivered SHIPPING >7 ngày |

### 📆 Định kỳ (tuần / tháng / năm)

| Job | Service | Mô Tả |
|-----|---------|--------|
| JOB-05 | payment-service | Dọn Outbox (PROCESSED >7d, FAILED >3d) |
| JOB-06 | payment-service | Dọn DLQ (RESOLVED >30d, DEAD >90d) |
| JOB-09 | notification-service | Notification TTL (MongoDB TTL Index) |
| JOB-10 | product-service | Hard delete sản phẩm đã soft-delete (CN) |

---

**Tài liệu cập nhật: 2026-04-22**

**Tài liệu cập nhật: 2026-04-14**



