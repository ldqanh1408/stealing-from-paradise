# Admin Service — Database Tables

> Stack: PostgreSQL
> Cập nhật: 2026-05-05

---

## ADMINS
Hồ sơ Admin (1:1 với USERS)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

> **Lưu ý:** Bảng ADMINS thuộc **identity-service** (PostgreSQL). Admin service không phải là service độc lập — các admin routes được xử lý trong service tương ứng và routed bởi API Gateway dưới `/admin/**`. Xem chi tiết tại [identity-service](../identity-service/03_database_tables.md).
