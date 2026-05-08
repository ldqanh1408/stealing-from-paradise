# Flash Sale Service — Database Tables

> Stack: PostgreSQL
> Cập nhật: 2026-05-08
> Model: Admin-Centralized + Dynamic Price Calculation + Auto-Approve Registration

---

## Tổng quan thiết kế

```
FS_SESSIONS
     │
     └── FS_ITEMS (Seller đăng ký PRODUCT — TỰ ĐỘNG DUYỆT)
              │
              └── Lưu discount_applied (%)
                  └── Giá flash sale tính DYNAMIC khi buyer mua (SKU price - discount%)
```

**Nguyên tắc:**
1. Admin tạo session
2. Seller đăng ký **PRODUCT** trước thời hạn đăng ký
3. **Registration deadline = start_time - 15 minutes** (VD: 8:00 - 15 phút = 7:45)
4. **Trước 7:45** → Được đăng ký | **Từ 7:45 đến 8:00** → Không được đăng ký
5. Seller đăng ký → **TỰ ĐỘNG DUYỆT** với discount của session
6. **Giá flash sale tính DYNAMIC** khi buyer mua: `flash_price = SKU_price * (1 - discount_applied / 100)`

---

## FS_SESSIONS
Session Flash Sale (theo khoảng thời gian)

```sql
CREATE TABLE fs_sessions (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL,
    start_time            TIMESTAMP NOT NULL,
    end_time              TIMESTAMP NOT NULL,
    registration_deadline TIMESTAMP NOT NULL,  -- auto-calculated: start_time - 15 minutes


    status                VARCHAR(20) NOT NULL DEFAULT 'UPCOMING',
    deleted_at            TIMESTAMP,
    created_at            TIMESTAMP DEFAULT NOW(),
    updated_at            TIMESTAMP DEFAULT NOW(),

    CONSTRAINT chk_status CHECK (status IN ('UPCOMING', 'ACTIVE', 'ENDED')),
    CONSTRAINT chk_time CHECK (end_time > start_time),
    CONSTRAINT chk_registration_deadline CHECK (registration_deadline < start_time),
    CONSTRAINT chk_discount CHECK (discount_percentage > 0 AND discount_percentage <= 100)
);

CREATE INDEX idx_fs_sessions_status ON fs_sessions(status);
CREATE INDEX idx_fs_sessions_time ON fs_sessions(start_time, end_time);
CREATE INDEX idx_fs_sessions_registration_deadline ON fs_sessions(registration_deadline);
```

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `name` | VARCHAR | Tên session (VD: "Flash Sale 8h sáng") |
| `start_time` | TIMESTAMP | Thời gian bắt đầu flash sale |
| `end_time` | TIMESTAMP | Thời gian kết thúc flash sale |
| `registration_deadline` | TIMESTAMP | **Deadline đăng ký = start_time - 15 minutes** |
| `status` | VARCHAR | UPCOMING / ACTIVE / ENDED |
| `deleted_at` | TIMESTAMP | Soft delete |
| `created_at` | TIMESTAMP | Thời tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## FS_ITEMS
Sản phẩm tham gia Flash Sale — **TỰ ĐỘNG DUYỆT** khi seller đăng ký

```sql
CREATE TABLE fs_items (
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT NOT NULL REFERENCES fs_sessions(id),
    product_id        UUID NOT NULL,                    -- PRODUCT (không phải SKU)

    -- Chỉ lưu discount %, giá tính DYNAMIC khi buyer mua
    discount_applied  DECIMAL(5,2) NOT NULL,             -- VD: 20.00 = 20%

    -- Thông tin seller
    seller_id         UUID NOT NULL,

    -- Timestamps
    registered_at     TIMESTAMP DEFAULT NOW(),           -- Thời điểm đăng ký
    created_at        TIMESTAMP DEFAULT NOW(),
    updated_at        TIMESTAMP DEFAULT NOW(),

    -- Constraints
    UNIQUE(session_id, product_id),
    CONSTRAINT chk_discount_applied CHECK (discount_applied > 0)
);

CREATE INDEX idx_fs_items_session ON fs_items(session_id);
CREATE INDEX idx_fs_items_product ON fs_items(product_id);
CREATE INDEX idx_fs_items_seller ON fs_items(seller_id);
```

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `session_id` | BIGINT | FK → FS_SESSIONS.id |
| `product_id` | UUID | **PRODUCT** tham gia flash sale |
| `discount_applied` | DECIMAL | **% giảm** — giá tính dynamic khi buyer mua |
| `seller_id` | UUID | ID seller đăng ký |
| `registered_at` | TIMESTAMP | Thời điểm đăng ký |
| `created_at` | TIMESTAMP | Thời điểm đăng ký |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## Luồng Registration Window

```
VD: Session bắt đầu lúc 8:00, registration_deadline = 7:45

◄──────────────────────────────────────────────►
│          SELLER ĐƯỢC ĐĂNG KÝ                 │

                                    7:45 ─────────── 8:00
                                         │               │
                                         │    KHÔNG ĐƯỢC
                                         │    ĐĂNG KÝ
                                         │
                                    registration_deadline
                                         (7:45)
                                         start_time
                                          (8:00)
```

**Quy tắc:**
- `registration_deadline = start_time - 15 minutes` (VD: 8:00 - 15 phút = **7:45**)
- **Được đăng ký:** `NOW() < registration_deadline` (trước 7:45)
- **Không được đăng ký:** `NOW() >= registration_deadline` (từ 7:45 trở đi)

---

## Luồng Dynamic Price Calculation

```
T+0: Seller đăng ký PRODUCT (trước 7:45)
     → discount_applied = session.discount_percentage (VD: 20%)
     → registered_at = NOW()  (TỰ ĐỘNG APPROVED)
     → KHÔNG lưu flash_price vào DB

T+1: Session ACTIVE (8:00)
     → Buyer chọn SKU của product
     → flash_price = SKU.price * (1 - discount_applied / 100)
     → Buyer mua với flash_price đã tính

T+2: Session ENDED (10:00)
     → fs_items không còn hiệu lực
     → product trở về giá thường
```

**Ví dụ khi Buyer mua:**
- SKU.price = 250,000 VND
- fs_items.discount_applied = 20%
- flash_price = 250,000 * 0.8 = **200,000 VND**

---

## Luồng trạng thái (Đơn giản hóa)

```
┌──────────────────────────────────────────────────────────────┐
│  FS_ITEMS Status Flow                                        │
│                                                              │
│  ┌─────────────┐    Seller đăng ký    ┌──────────────────┐  │
│  │   (new)     │ ──────────────────→  │ APPROVED (tự động)│ │
│  └─────────────┘                      └──────────────────┘  │
│                                                              │
│  NOTE: Không có PENDING/REJECTED                            │
│        Seller đăng ký thành công = Được duyệt ngay          │
└──────────────────────────────────────────────────────────────┘
```
