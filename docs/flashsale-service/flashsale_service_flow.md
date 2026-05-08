# Flash Sale Service — Luồng hoạt động

## Tổng quan kiến trúc

Flash Sale Service quản lý các session flash sale với cơ chế:
- **Registration Window**: Seller chỉ được đăng ký trong khoảng thời gian cho phép
- **Auto-Approve**: Seller đăng ký → Tự động được duyệt với discount của session
- **Dynamic Price**: Giá flash sale tính DYNAMIC khi buyer mua (không snapshot)

```
Client / Admin Dashboard
  │
  ▼
API Gateway
  │
  ├──[Sync]──► Flash Sale Service ◄──[Sync]── Product Service (lấy thông tin SKU price)
  │                 │
  │                 ├──[Async / Kafka]──► Product Service (thông báo session started/ended)
  │                 │                        │
  │                 │                        ├──[Async / Kafka]──► Search Service (sync flash sale price)
  │                 │                  
  │                 │
  │                 └──[Read/Write]────► PostgreSQL + Redis
  │
  └──[Sync]──► Search Service (tìm kiếm sản phẩm flash sale)
```

**Nguyên tắc ownership:**
- **Flash Sale Service**: Nắm thông tin `% giảm giá` của từng product trong session
- **Product Service**: Nắm `giá gốc SKU`, tính toán `flash_price = sku_price * (1 - discount/100)`, sau đó bắn event cho Search Service
- **Search Service**: Nhận data đã hoàn chỉnh, update Elasticsearch để hiển thị

---

## 1. Luồng Admin quản lý Session

### 1.1 Admin tạo Session mới

```
Admin POST /admin/sessions
  {
    "name": "Flash Sale 8h sáng",
    "start_time": "2026-05-10 08:00:00",
    "end_time": "2026-05-10 10:00:00"
  }
  │
  ├── Validate input
  ├── Tính registration_deadline = start_time - 15 minutes
  │     (VD: 08:00 - 15 phút = 07:45)
  │
  ├── Tạo bản ghi fs_sessions:
  │     status = 'UPCOMING'
  │     registration_deadline = 07:45
  │
  └── Return 201 {
         id: 1,
         name: "Flash Sale 8h sáng",
         start_time: "08:00",
         end_time: "10:00",
         registration_deadline: "07:45",
         status: "UPCOMING"
       }
```

### 1.2 Admin xem danh sách Session

```
Admin GET /admin/sessions
  │
  ├── Lấy danh sách tất cả session (UPCOMING, ACTIVE, ENDED)
  │
  └── Trả về danh sách với thống kê:
       - Số product đã đăng ký
       - Thời gian còn lại đến deadline
```

---

## 2. Luồng Seller đăng ký Product

### 2.1 Seller xem danh sách Session sắp tới

```
Seller GET /sessions?status=UPCOMING
  │
  ├── Trả về danh sách session có status = 'UPCOMING'
  ├── Mỗi session bao gồm:
  │     ├── id, name, start_time, end_time
  │     ├── registration_deadline
  │     └── is_registration_open = (NOW() < registration_deadline)
  │
  └── Seller biết được:
       - Còn bao lâu để đăng ký
```

### 2.2 Seller đăng ký Product tham gia Session (TỰ ĐỘNG DUYỆT)

```
Seller POST /sessions/:session_id/register
  {
    "product_id": "uuid-của-product"
  }
  │
  ├── 1. Kiểm tra session tồn tại và status = 'UPCOMING'
  │
  ├── 2. KIỂM TRA REGISTRATION DEADLINE:
  │     IF (NOW() >= session.registration_deadline)
  │       THEN return 400 {
  │         error: "REGISTRATION_CLOSED",
  │         message: "Đăng ký đã đóng lúc 07:45. Không thể đăng ký sau thời điểm này."
  │       }
  │
  ├── 3. Kiểm tra product thuộc về seller (gọi Product Service)
  │
  ├── 4. Kiểm tra product chưa được đăng ký trong session này
  │     (UNIQUE constraint: session_id + product_id)
  │
  ├── 5. Tạo fs_items với discount tùy ý của seller
  │     discount_applied = <tùy ý seller>
  │     registered_at = NOW()
  │     (KHÔNG cần lấy giá, không snapshot)
  │
  └── 6. Return 201 {
         id: 123,
         status: 'APPROVED',
         message: 'Đăng ký thành công! Giảm 20% cho tất cả SKU.',
         discount_applied: 20.00
       }
```

### 2.3 Seller xem danh sách đăng ký của mình

```
Seller GET /my-registrations?session_id=:id
  │
  ├── Lấy danh sách fs_items của seller trong session
  ├── Bao gồm: product_id, discount_applied, registered_at
  │
  └── Trả về danh sách đã đăng ký:
       [{
         product_id: "...",
         discount_applied: 20.00,
         registered_at: "2026-05-09 07:50:00"
       }]
```

---

## 3. Luồng Session hoạt động

### 3.1 Session chuyển sang ACTIVE (Scheduled Job)

```
Cron Job: Mỗi phút kiểm tra fs_sessions
  │
  FOR EACH session WHERE status = 'UPCOMING' AND start_time <= NOW():
  │
  ├── Cập nhật fs_sessions.status = 'ACTIVE'
  │
  ├── [Async] Kafka topic: flash_sale.session_started
  │     → Product Service nhận event
  │       ├── Lấy danh sách fs_items trong session
  │       ├── Với mỗi fs_item:
  │       │     ├── Query product_variant để lấy sku_price
  │       │     └── Tính price = original_price * (1 - discount_applied / 100)
  │       ├── [Async] Kafka topic: flash_sale.price_sync
  │       │     → Search Service update Elasticsearch
  │       │
  │       
  │
```

### 3.2 Session kết thúc (Scheduled Job)

```
Cron Job: Mỗi phút kiểm tra fs_sessions
  │
  FOR EACH session WHERE status = 'ACTIVE' AND end_time <= NOW():
  │
  ├── Cập nhật fs_sessions.status = 'ENDED'
  │
  ├── [Async] Kafka topic: flash_sale.session_ended
  │     → Product Service nhận event
  │       ├── Lấy danh sách fs_items trong session
  │       ├── [Async] Kafka topic: flash_sale.price_sync (reset price)
  │       │     → Search Service update: flash_price = original_price, has_discount = false
  │       │
  │       └── [Async] Kafka topic: flash_sale.notify_ended
  │           
  │
```

---

## 5. Luồng Timeline

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Timeline của một Session                                                        │
│                                                                                │
│  ◄────────────────────── ĐĂNG KÝ (trước 7:45) ──────────────────────► 7:45  │
│                                                                                │
│                                                                              8:00 │
│   │                                      │                                        │
│   │                    KHÔNG ĐƯỢC ĐĂNG KÝ (từ 7:45 đến 8:00) ──────────►   │
│   │                                      │                                        │
│                                             │                                        │
│                                             │ ◄────────── 2 giờ ─────────►      │
│                                             │          ACTIVE                      │
│                                             │                                        │
│                                                                                │
│  T+0: Seller đăng ký PRODUCT (trước 7:45)                                        │
│       → discount_applied = 20%                                                   │
│       → registered_at = NOW()                                                     │
│       → KHÔNG snapshot giá                                                       │
│                                                                                │
│  T+1: Session ACTIVE (8:00)                                                       │
│       → Buyer chọn SKU và mua                                                     │
│                                                                                │
│  T+2: Session ENDED (10:00)                                                       │
│       → fs_items không còn hiệu lực                                               │
│                                                                                │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Error Codes

| Code | HTTP Status | Mô tả |
|------|-------------|-------|
| `SESSION_NOT_FOUND` | 404 | Session không tồn tại |
| `SESSION_NOT_UPCOMING` | 400 | Session không ở trạng thái UPCOMING |
| `SESSION_NOT_ACTIVE` | 400 | Session không ở trạng thái ACTIVE |
| `REGISTRATION_CLOSED` | 400 | Đã hết hạn đăng ký (qua deadline) |
| `PRODUCT_ALREADY_REGISTERED` | 409 | Product đã được đăng ký trong session này |
| `PRODUCT_NOT_OWNED` | 403 | Product không thuộc sở hữu của seller |
| `PRODUCT_NOT_IN_FLASH_SALE` | 400 | Product không tham gia flash sale này |
| `SKU_NOT_FOUND` | 404 | SKU không tồn tại trong product |

---

## 7. Kafka Topics

### 7.1 Topics do Flash Sale Service publish

| Topic | Consumer | Payload | Mục đích |
|-------|----------|---------|----------|
| `flash_sale.session_started` | Product Service | `{session_id, name, start_time, end_time}` | Trigger Product Service tính flash_price |
| `flash_sale.session_ended` | Product Service | `{session_id, name}` | Trigger Product Service reset price về gốc |

### 7.2 Topics do Product Service publish

| Topic | Consumer | Payload | Mục đích |
|-------|----------|---------|----------|
| `flash_sale.price_sync` | Search Service | `{items: [{sku_id, flash_price, original_price, has_discount, discount_pct, session_id}]}` | Update Elasticsearch với flash price đã tính |

### 7.3 Event Payload chi tiết

```json
// Topic: flash_sale.session_started (Flash Sale → Product Service)
{
  "event": "flash_sale.session_started",
  "session_id": 1,
  "name": "Flash Sale 8h sáng",
  "start_time": "2026-05-10T08:00:00Z",
  "end_time": "2026-05-10T10:00:00Z",
  "timestamp": "2026-05-10T08:00:00Z"
}

// Topic: flash_sale.price_sync (Product Service → Search Service)
{
  "event": "flash_sale.price_sync",
  "action": "activate",
  "session_id": 1,
  "items": [
    {
      "sku_id": "sku-001",
      "product_id": "prod-123",
      "flash_price": 160000,
      "original_price": 200000,
      "has_discount": true,
      "discount_pct": 20
    }
  ],
  "timestamp": "2026-05-10T08:00:00Z"
}

// Topic: flash_sale.session_ended (Flash Sale → Product Service)
{
  "event": "flash_sale.session_ended",
  "session_id": 1,
  "name": "Flash Sale 8h sáng",
  "timestamp": "2026-05-10T10:00:00Z"
}

// Topic: flash_sale.price_sync (Product Service → Search Service) - DEACTIVATE
{
  "event": "flash_sale.price_sync",
  "action": "deactivate",
  "session_id": 1,
  "items": [
    { "sku_id": "sku-001", "product_id": "prod-123" }
  ],
  "timestamp": "2026-05-10T10:00:00Z"
}
```

### 7.4 Event Flow hoàn chỉnh

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  KHI SESSION CHUYỂN SANG ACTIVE                                                     │
│                                                                                     │
│  Cron Job                                                                          │
│      │                                                                              │
│      ▼                                                                              │
│  ┌──────────────────────────────┐                                                    │
│  │  Flash Sale Service          │                                                    │
│  │  status = 'ACTIVE'           │                                                    │
│  └──────────────┬───────────────┘                                                    │
│                 │ Kafka: flash_sale.session_started                                  │
│                 ▼                                                                     │
│  ┌──────────────────────────────┐                                                    │
│  │  Product Service             │                                                    │
│  │                              │  1. Lấy fs_items (product_id, discount)            │
│  │  TÍNH TOÁN:                  │  2. Query variant để lấy sku_price               │
│  │  flash_price = price *        │  3. flash_price = sku_price * (1 - discount/100) │
│  │    (1 - discount/100)        │                                                    │
│  └──────────────┬───────────────┘                                                    │
│                 │ Kafka: flash_sale.price_sync (action=activate)                    │
│                 ▼                                                                     │
│  ┌──────────────────────────────┐                                                    │
│  │  Search Service              │  UPDATE ES:                                         │
│  │    - price = flash_price    │                                                    │
│  │    - original_price          │                                                    │
│  │    - has_discount = true    │                                                    │
│  └──────────────────────────────┘                                                    │
│                                                                                                 │
│  ┌──────────────────────────────┐                                                    │
│                         │
│  └──────────────────────────────┘                                                    │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│  KHI SESSION KẾT THÚC                                                                │
│                                                                                     │
│  Cron Job                                                                          │
│      │                                                                              │
│      ▼                                                                              │
│  ┌──────────────────────────────┐                                                    │
│  │  Flash Sale Service          │                                                    │
│  │  status = 'ENDED'           │                                                    │
│  └──────────────┬───────────────┘                                                    │
│                 │ Kafka: flash_sale.session_ended                                    │
│                 ▼                                                                     │
│  ┌──────────────────────────────┐                                                    │
│  │  Product Service             │  Reset price về original_price                      │
│  └──────────────┬───────────────┘                                                    │
│                 │ Kafka: flash_sale.price_sync (action=deactivate)                  │
│                 ▼                                                                     │
│  ┌──────────────────────────────┐                                                    │
│  │  Search Service              │  UPDATE ES:                                         │
│  │    - price = original_price │                                                    │
│  │    - has_discount = false   │                                                    │
│  └──────────────────────────────┘                                                    │
└─────────────────────────────────────────────────────────────────────────────────────┘
```
