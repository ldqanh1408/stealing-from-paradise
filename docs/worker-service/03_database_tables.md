# Worker Service — Database Tables

> Stack: PostgreSQL · Axon
> Cập nhật: 2026-05-05

---

## OUTBOX_EVENTS
Event Outbox Pattern (cho eventual consistency)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `topic` | VARCHAR | Tên topic/event |
| `payload` | JSONB | Nội dung event |
| `status` | VARCHAR | PENDING \| PROCESSED \| FAILED |
| `retry_count` | INT | Số lần retry |
| `processed_at` | TIMESTAMP | Thời điểm xử lý |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## FAILED_EVENTS
Lưu trữ event/task lỗi để xử lý thủ công

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `topic_or_task` | VARCHAR | Tên topic hoặc task |
| `payload` | JSONB | Payload bị lỗi |
| `error_reason` | TEXT | Lý do lỗi |
| `retry_count` | INT | Số lần retry |
| `status` | VARCHAR | PENDING \| DEAD \| RESOLVED \| MANUAL_INTERVENTION |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## SHEDLOCK
Distributed Lock cho scheduled jobs (ShedLock)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `name` | VARCHAR | Primary Key, tên lock |
| `lock_until` | TIMESTAMP | Thời điểm hết lock |
| `locked_at` | TIMESTAMP | Thời điểm bắt đầu lock |
| `locked_by` | VARCHAR | Node/thread đang giữ lock |

---

## Ghi Chú

- Worker service quản lý **Outbox Relay**: đọc OUTBOX_EVENTS với `FOR UPDATE SKIP LOCKED`, publish lên Kafka, cập nhật status → PROCESSED.
- **DLQ (Dead Letter Queue)**: Tự động retry FAILED_EVENTS theo lịch cron, chuyển sang DEAD sau N lần thất bại.
- **ShedLock**: Dùng distributed lock để đảm bảo chỉ một instance chạy scheduled job tại một thời điểm.
- Các service khác (payment, ai-chat, v.v.) có thể có bảng OUTBOX_EVENTS riêng trong database local của chúng; worker-service chịu trách nhiệm relay tổng thể.
