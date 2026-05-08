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
  │                 ├──[Read/Write]────► PostgreSQL + Redis (scheduled triggers)
  │                 │                        │
  │                 │                        └── Redis ZSET: flash_sale:triggers
  │
  └──[Sync]──► Search Service (tìm kiếm sản phẩm flash sale)
```

**Nguyên tắc ownership:**
- **Flash Sale Service**: Nắm thông tin `% giảm giá` của từng product trong session
- **Product Service**: Nắm `giá gốc SKU`, tính toán `flash_price = sku_price * (1 - discount/100)`, sau đó bắn event cho Search Service
- **Search Service**: Nhận data đã hoàn chỉnh, update Elasticsearch để hiển thị

**Nguyên tắc Trigger không độ trễ:**
- Sử dụng **Redis Sorted Set (ZSET)** để lưu các trigger theo Unix timestamp
- **Độ trễ ≈ 0ms** thay vì độ trễ tối đa 1 phút như cron job
- Redis Worker liên tục đọc và trigger ngay khi đến thời điểm

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

### 3.0. Redis Trigger Data Structure

```
Redis Sorted Set: flash_sale:triggers
  ├── Score: Unix timestamp (milliseconds)
  └── Member: JSON string
        {
          "type": "session_start" | "session_end",
          "session_id": 1
        }

VD: Khi admin tạo session (start_time=08:00, end_time=10:00)
    ZADD flash_sale:triggers 1715304000000 '{"type":"session_start","session_id":1}'
    ZADD flash_sale:triggers 1715311200000 '{"type":"session_end","session_id":1}'
```

### 3.1. Admin tạo Session (Đăng ký triggers vào Redis)

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
  │
  ├── Tạo bản ghi fs_sessions:
  │     status = 'UPCOMING'
  │     registration_deadline = 07:45
  │
  ├── Đăng ký trigger vào Redis:
  │     ZADD flash_sale:triggers <start_time_unix_ms> '{"type":"session_start","session_id":<id>}'
  │     ZADD flash_sale:triggers <end_time_unix_ms> '{"type":"session_end","session_id":<id>}'
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

### 3.2. Redis Worker — Trigger Session ACTIVE (độ trễ ≈ 0ms)

```
Redis Worker (Background Process)
  │
  ├── Loop forever:
  │     │
  │     ├── ZRANGEBYSCORE flash_sale:triggers -inf <current_timestamp_ms> LIMIT 0 10
  │     │     (Lấy tất cả trigger có score <= NOW)
  │     │
  │     └── FOR EACH trigger:
  │           │
  │           ├── ZREM flash_sale:triggers <trigger>  (Xóa khỏi ZSET)
  │           │
  │           ├── IF type = "session_start":
  │           │     │
  │           │     ├── UPDATE fs_sessions SET status = 'ACTIVE' WHERE id = <session_id>
  │           │     │
  │           │     ├── [Async] Kafka topic: flash_sale.session_started
  │           │     │     → Product Service nhận event
  │           │     │       ├── Lấy danh sách fs_items trong session
  │           │     │       ├── Với mỗi fs_item:
  │           │     │       │     ├── Query product_variant để lấy sku_price
  │           │     │       │     └── Tính price = original_price * (1 - discount_applied / 100)
  │           │     │       ├── [Async] Kafka topic: flash_sale.price_sync
  │           │     │             → Search Service update Elasticsearch
  │           │     │
  │           │
  │           └── IF type = "session_end":
  │                 │
  │                 ├── UPDATE fs_sessions SET status = 'ENDED' WHERE id = <session_id>
  │                 │
  │                 ├── [Async] Kafka topic: flash_sale.session_ended
  │                 │     → Product Service nhận event
  │                 │       ├── Lấy danh sách fs_items trong session
  │                 │       ├── [Async] Kafka topic: flash_sale.price_sync (reset price)
  │                 │       │     → Search Service update: flash_price = original_price
  │                 │       │
  │                 └── [Async] Kafka topic: flash_sale.notify_ended
```

**Ưu điểm so với Cron Job:**
| Cron Job | Redis Trigger |
|----------|---------------|
| Độ trễ tối đa 60 giây | Độ trễ ≈ 0ms |
| Check mỗi 60 giây | Trigger chính xác vào thời điểm |
| Xử lý nhiều session cùng lúc (lãng phí) | Chỉ xử lý đúng session cần trigger |
| Cần lock để tránh duplicate | Atomic ZPOPMIN đảm bảo single execution |

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
│  KHI SESSION CHUYỂN SANG ACTIVE (Redis Trigger — độ trễ ≈ 0ms)                   │
│                                                                                     │
│  Redis Worker                                                                       │
│      │                                                                              │
│      │ ZRANGEBYSCORE flash_sale:triggers -inf <now> LIMIT 0 10                    │
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
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│  KHI SESSION KẾT THÚC (Redis Trigger — độ trễ ≈ 0ms)                                │
│                                                                                     │
│  Redis Worker                                                                       │
│      │                                                                              │
│      │ ZRANGEBYSCORE flash_sale:triggers -inf <now> LIMIT 0 10                      │
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

---

## 8. Redis Trigger — Chi tiết Implementation

### 8.1 Redis Key Structure

```
Key: flash_sale:triggers
Type: Sorted Set (ZSET)
```

| Key | Score | Member |
|-----|-------|--------|
| `flash_sale:triggers` | Unix timestamp (ms) | `{"type":"session_start","session_id":1}` |
| `flash_sale:triggers` | Unix timestamp (ms) | `{"type":"session_end","session_id":1}` |

### 8.2 Operations

```redis
-- Khi admin tạo session (start=08:00, end=10:00)
ZADD flash_sale:triggers 1715304000000 '{"type":"session_start","session_id":1}'
ZADD flash_sale:triggers 1715311200000 '{"type":"session_end","session_id":1}'

-- Khi Redis Worker chạy (đọc trigger đến hạn)
ZRANGEBYSCORE flash_sale:triggers -inf 1715304000000 LIMIT 0 10

-- Sau khi xử lý xong (atomic)
ZREM flash_sale:triggers '{"type":"session_start","session_id":1}'
```

### 8.3 Redis Worker Pseudocode

```python
import redis
import time
import json

r = redis.Redis(host='localhost', port=6379, db=0)

while True:
    # Lấy tất cả trigger có score <= NOW
    current_ts_ms = int(time.time() * 1000)
    triggers = r.zrangebyscore('flash_sale:triggers', '-inf', current_ts_ms, start=0, num=10)

    for trigger_json in triggers:
        trigger = json.loads(trigger_json)

        # Atomic remove (tránh double process)
        removed = r.zrem('flash_sale:triggers', trigger_json)
        if not removed:
            continue  # Đã được process bởi worker khác

        session_id = trigger['session_id']

        if trigger['type'] == 'session_start':
            activate_session(session_id)
        elif trigger['type'] == 'session_end':
            end_session(session_id)

    # Sleep ngắn để tránh CPU spam, nhưng vẫn responsive
    time.sleep(0.1)  # 100ms = độ trễ tối đa 100ms thay vì 60s
```

### 8.4 So sánh độ trễ

| Phương pháp | Độ trễ tối đa | Độ chính xác |
|-------------|---------------|--------------|
| Cron 1 phút | 60,000ms | ±30 giây |
| Redis Trigger (sleep 100ms) | **100ms** | ±50ms |
| Redis Blocking `BZPOPMIN` | **0ms** | Chính xác |

