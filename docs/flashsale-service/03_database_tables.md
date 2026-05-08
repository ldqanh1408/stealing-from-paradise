# Flash Sale Service — Database Tables

> Stack: PostgreSQL
> Cập nhật: 2026-05-08
> Model: Admin-Centralized + Snapshot Pricing

---

## Tổng quan thiết kế

```
FS_SESSIONS
     ├
     │
     └── FS_ITEMS (Seller đăng ký SKU, Admin duyệt)
              │
              └── SNAPSHOT giá tại thời điểm duyệt — KHÔNG recalculate
```

**Nguyên tắc:**
1. Admin tạo session + set discount rules theo category
2. Seller đăng ký SKU tham gia
3. Giá được SNAPSHOT tại thời điểm Admin duyệt — không thay đổi trong suốt session

---

## FS_SESSIONS
Session Flash Sale (theo khoảng thời gian)

```sql
CREATE TABLE fs_sessions (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    start_time  TIMESTAMP NOT NULL,
    end_time    TIMESTAMP NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'UPCOMING',
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW(),

    CONSTRAINT chk_status CHECK (status IN ('UPCOMING', 'ACTIVE', 'ENDED')),
    CONSTRAINT chk_time CHECK (end_time > start_time)
);

CREATE INDEX idx_fs_sessions_status ON fs_sessions(status);
CREATE INDEX idx_fs_sessions_time ON fs_sessions(start_time, end_time);
```

---

## FS_ITEMS
Sản phẩm tham gia Flash Sale — Snapshot giá tại thời điểm duyệt

```sql
CREATE TABLE fs_items (
    id                      BIGSERIAL PRIMARY KEY,
    session_id              BIGINT NOT NULL REFERENCES fs_sessions(id),
    sku_code                VARCHAR(100) NOT NULL,

    -- Snapshot giá tại thời điểm đăng ký
    original_price_snapshot DECIMAL(18,2) NOT NULL,  -- Giá gốc tại T+0
    flash_price             DECIMAL(18,2) NOT NULL, -- GIÁ CỐ ĐỊNH (snapshot)

    -- Discount đã duyệt (để hiển thị "Giảm X%")
    discount_applied       DECIMAL(5,2),            -- VD: 10.00 = 10%

    -- Quản lý
    seller_id               UUID NOT NULL,          -- ID từ Seller/User Service
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note              TEXT,                   -- Ghi chú khi duyệt/từ chối
    rejected_reason         TEXT,

    -- Timestamps
    approved_at             TIMESTAMP,              -- Thời điểm duyệt (snapshot price locked)
    created_at              TIMESTAMP DEFAULT NOW(),
    updated_at              TIMESTAMP DEFAULT NOW(),

    -- Constraints
    UNIQUE(session_id, sku_code),
    CONSTRAINT chk_flash_price CHECK (flash_price > 0),
    CONSTRAINT chk_flash_stock CHECK (flash_stock >= 0),
    CONSTRAINT chk_sold_qty CHECK (sold_qty >= 0 AND sold_qty <= flash_stock)
);

CREATE INDEX idx_fs_items_session ON fs_items(session_id);
CREATE INDEX idx_fs_items_sku ON fs_items(sku_code);
CREATE INDEX idx_fs_items_status ON fs_items(status);
CREATE INDEX idx_fs_items_seller ON fs_items(seller_id);
CREATE INDEX idx_fs_items_category ON fs_items(category_id);
```

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `session_id` | BIGINT | FK → FS_SESSIONS.id |
| `sku_code` | VARCHAR | SKU tham gia flash sale |
| `original_price_snapshot` | DECIMAL | Giá gốc tại thời điểm đăng ký (snapshot — không thay đổi) |
| `flash_price` | DECIMAL | **Giá flash sale CỐ ĐỊNH** — snapshot tại thời điểm duyệt |
| `discount_applied` | DECIMAL | % giảm đã duyệt (VD: 10.00 = 10%) |
| `seller_id` | UUID | ID seller đăng ký |
| `status` | VARCHAR | PENDING → APPROVED → REJECTED|
| `admin_note` | TEXT | Ghi chú khi duyệt |
| `rejected_reason` | TEXT | Lý do từ chối |
| `approved_at` | TIMESTAMP | Thời điểm duyệt — **giá được lock tại đây** |
| `created_at` | TIMESTAMP | Thời điểm đăng ký |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## Luồng snapshot giá

```
T+0: Seller đăng ký SKU
     → Lấy product_variant.price → lưu vào original_price_snapshot
     → fs_items.status = PENDING

T+1: Admin duyệt
     → flash_price được SET = GIÁ CỐ ĐỊNH
     → approved_at = NOW()
     → fs_items.status = APPROVED
     → Giá KHÔNG thay đổi dù seller có sửa price sau này

T+2: Session ACTIVE
     → Buyer mua với flash_price (đã lock)

T+3: Session ENDED
     → fs_items không còn hiệu lực
     → product_variant trở về giá thường
```

---

## Luồng trạng thái

```
┌──────────────────────────────────────────────────────────────┐
│  FS_ITEMS Status Flow                                        │
│                                                              │
│  ┌─────────┐    Seller submit     ┌──────────┐              │
│  │ (new)   │ ──────────────────→  │ PENDING  │              │
│  └─────────┘                      └─────┬────┘              │
│                                        │                     │
│                        ┌───────────────┼                      │
│                        ↓               ↓                      │
│                 ┌──────────┐   ┌──────────┐                   │
│                 │ APPROVED │   │ REJECTED │                   │
│                 └──────────┘   └──────────┘                   │
│                                                              │
│  NOTE: Sau APPROVED, KHÔNG thể chuyển sang CANCELLED        │
│        trong cùng session (chỉ ENDED tự động khi hết hạn)   │
└──────────────────────────────────────────────────────────────┘
```


