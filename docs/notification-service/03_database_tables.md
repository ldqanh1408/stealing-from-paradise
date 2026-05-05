# Notification Service — Database Tables

> Stack: MongoDB
> Cập nhật: 2026-05-05

---

## MG_NOTIFICATIONS (MongoDB)
Thông báo cho Buyer, Seller, Admin

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | VARCHAR | PK, Mongo ObjectId |
| `user_id` | BIGINT | FK → USERS.id |
| `title` | VARCHAR | Tiêu đề thông báo |
| `body` | TEXT | Nội dung thông báo |
| `type` | VARCHAR | Loại thông báo |
| `metadata` | JSONB | Dữ liệu bổ sung |
| `is_read` | BOOLEAN | Đã đọc hay chưa |
| `created_at` | TIMESTAMP | Thời điểm tạo (TTL Index 90 ngày) |
