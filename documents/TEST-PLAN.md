# Kế hoạch kiểm thử toàn bộ Business Flow & Use Case — Backend (Docker Compose dev)

Cập nhật: 2026-06-10. Nguồn use case: `documents/use-cases/` (44 UC / 9 service), traceability: `documents/traceability/`.

## 0. Môi trường & ràng buộc

- Stack: full dev compose (`.\flashsale-build.ps1 dev` hoặc `make dev`) — 25 container đang chạy, gồm `fs-stripe-listener` (forward webhook thật) và `fs-kafka-connect` (Debezium connector `postgres-outbox-connector` đã đăng ký).
- Stripe TEST mode đã cấu hình sẵn (account `acct_1T9nfQCma465d2uk`).
- **CẤM xóa volume** (postgres/mongo/axon/kafka/...): cấu hình Stripe (seller accounts, onboarding state) nằm trong dữ liệu này. Không dùng `make clean`, không `docker volume rm`.
- Seed dev: users (sellers 1-5, buyers 6-9 & 11-13, admin 10, password `dev123`), products + inventory, orders mẫu.
- Suite tự động: `backend/e2e-tests` — chạy `cd backend && mvn -pl e2e-tests test -Pe2e`. Kiểm thử UI: Claude-in-Chrome MCP (màn hình Arzopa) — frontends `localhost:3000/3001/3002`, Stripe Dashboard: https://dashboard.stripe.com/acct_1T9nfQCma465d2uk/test

## 1. Ma trận Use Case ↔ Phương pháp kiểm thử

Ký hiệu: ✅ = đã có test tự động; 🆕 = cần bổ sung test tự động; 🖥️ = kiểm thử UI qua Chrome MCP; 👁 = quan sát/log-verify (không assert tự động được trong thời gian hợp lý).

### identity-service
| UC | Flow | Phương pháp |
|----|------|-------------|
| uc-001 register | Đăng ký buyer mới qua gateway | 🆕 A12 |
| uc-002 login | Login + JWT + sai mật khẩu + chặn route bảo vệ | ✅ A02 |
| uc-003 manage-profile | Xem/sửa profile `/users/me` | 🆕 A12 |
| uc-004 manage-addresses | CRUD địa chỉ | 🆕 A12 (hiện chỉ đọc trong helper checkout) |
| uc-006 seller-register | Đăng ký seller | 🆕 A12 |

### product-service
| UC | Flow | Phương pháp |
|----|------|-------------|
| uc-001 browse-catalog | List/detail public | ✅ A03 |
| uc-002 manage-categories | Admin CRUD category | 🆕 A11 mở rộng |
| uc-003 create-product | Seller tạo product | ✅ A11 |
| uc-004 manage-variants | Seller CRUD variant | 🆕 A11 mở rộng |
| uc-005 upload-images | Presigned URL MinIO + upload | 🆕 A11 mở rộng (assert presigned URL trả về) |
| uc-006 manage-stock | Seller cập nhật tồn kho | 🆕 A11 mở rộng |
| uc-007 reserve-stock | Reserve/release khi checkout | ✅ gián tiếp qua A04 (checkout) + 🆕 assert release khi cancel |
| uc-008..011 cart view/add/update/remove | Giỏ hàng đầy đủ | ✅ A03 (view/add) + 🆕 update/remove |
| uc-012 submit-product-review | Buyer review sau DELIVERED | 🆕 A13 |
| uc-013..015 pending/approve/reject | Admin duyệt product | ✅ A11 |

### order-service
| UC | Flow | Phương pháp |
|----|------|-------------|
| uc-001 checkout | Cart → preview → submit → PENDING + PaymentIntent | ✅ A04 |
| uc-002 view-orders | Buyer xem đơn | ✅ A02/A04 |
| uc-003 cancel-order | Buyer hủy PENDING → tx CANCELLED | ✅ A04 |
| uc-004 ship-order | Seller cập nhật tracking → SHIPPING | ✅ A05 |
| uc-005 confirm-delivery | Buyer xác nhận → DELIVERED | ✅ A05 |
| uc-006 request-return (RTS) | Seller return-to-sender (multipart + evidence) | 🆕 A14 |
| uc-007 view-seller-orders | Seller xem đơn của shop | 🆕 A14 |
| uc-008 seller-cancel-order | Seller hủy → refund flow + seller.order_cancelled | 🆕 A14 |
| (saga) payment-timeout | PENDING 30' → auto-cancel (Axon deadline) | 👁 verify deadline được schedule trong log saga; không chờ 30' |

### payment-service
| UC | Flow | Phương pháp |
|----|------|-------------|
| uc-001 onboard-stripe | Seller Connect onboarding (hosted UI) | 🖥️ seller-fe :3001 → Stripe hosted onboarding → verify trên Dashboard (Connect → Accounts) |
| uc-002 process-payment | PAYMENT_REQUESTED → PaymentIntent PENDING | ✅ A04 |
| uc-003 handle-webhook | payment_intent.succeeded/failed → PAID/CANCELLED | ✅ A04 (forged HMAC) + 🖥️ thanh toán thật bằng test card `4242 4242 4242 4242` trên customer-fe :3000 → webhook thật qua stripe-listener |
| uc-007 transfer-to-seller | DELIVERED → AWAITING_DELIVERY → transfer | ✅ A10 (state) + 🖥️ verify transfer trên Dashboard (Payments → Transfers) |
| uc-008 view-transfers | Seller xem transfers | ✅ A10 |
| (edge) transfer reversal | Refund sau transfer → reversal | 🆕 A15 + 🖥️ Dashboard |
| (edge) charge.refunded webhook | refund.stripe_auto flow | 🆕 A15 (forge charge.refunded) |

### refund-service
| UC | Flow | Phương pháp |
|----|------|-------------|
| uc-001 create-refund | Partial refund request sau DELIVERED | ✅ A05 |
| uc-002 approve-refund | Admin duyệt → Stripe refund thật → order REFUNDED | 🆕 A15 — **điều kiện: đơn thanh toán THẬT** (qua 🖥️ test card), vì PI forge không refund được trên Stripe |
| uc-003 reject-refund | Admin từ chối → refund REJECTED | 🆕 A15 |

### flashsale-service
| UC | Flow | Phương pháp |
|----|------|-------------|
| uc-001 create-session | Admin tạo session | ✅ A06 |
| uc-002 register-product | Seller đăng ký item | ✅ A06 |
| uc-003 view-sessions | Xem session active | ✅ A06 |
| uc-006 end-session | Kết thúc session + release stock | 🆕 A06 mở rộng |
| (flow) buy + reminder | Buyer mua flash sale + đặt/hủy reminder | ✅ A06 |

### search-service
| UC | Flow | Phương pháp |
|----|------|-------------|
| uc-001 search-products | Search + suggestions | ✅ A08 |
| uc-003 reindex | Admin reindex | ✅ A08 |
| (flow) CDC index sync | product mới → tự động vào index | 🆕 A08 mở rộng (tạo product → chờ search thấy) |

### notification-service
| UC | Flow | Phương pháp |
|----|------|-------------|
| uc-001 stream-notifications | SSE realtime | 🆕 A09 mở rộng (mở SSE, trigger order event, assert nhận message) |
| uc-002 view-history | Lịch sử thông báo | ✅ A09 |
| uc-003 mark-read | Đánh dấu đã đọc | ✅ A09 |
| (flow) outbox fan-out | order.paid (outbox→Debezium→Kafka) → notification | 🆕 A09 mở rộng — đồng thời verify Debezium relay hoạt động |

### ai-chat-service
| UC | Flow | Phương pháp |
|----|------|-------------|
| uc-001 start-chat | Tạo session | ✅ A07 |
| uc-002 send-message | Chat + LLM trả lời | ✅ A07 (cần LLM key hợp lệ trong `.env`) |
| uc-003 confirm-action | Human-in-the-loop xác nhận tool call | 🆕 A07 mở rộng (pending confirmation → confirm) |

## 2. Các phase thực thi

**Phase 1 — Baseline tự động (đang chạy):** `mvn -pl e2e-tests test -Pe2e` toàn bộ A01–A11; ghi nhận pass/fail, sửa lỗi suite hoặc bug backend phát hiện được.

**Phase 2 — Bổ sung suite tự động (🆕):** A12 identity, A13 review, A14 seller-order/RTS, A15 refund admin + transfer reversal; mở rộng A03/A06/A07/A08/A09/A11 như bảng trên. Mỗi suite tự dọn dữ liệu nó tạo (không đụng seed).

**Phase 3 — Kiểm thử Stripe qua UI (🖥️, Chrome MCP trên màn Arzopa):**
1. Onboarding: login seller-fe :3001 (`techworld/dev123`) → Payments → Connect Stripe → hoàn tất hosted onboarding (test data) → verify account trên Dashboard Connect và `SELLER_STRIPE_ACCOUNTS.charges_enabled=true`.
2. Thanh toán thật: customer-fe :3000 (`minhhoa/dev123`) → mua hàng → Stripe Elements card `4242 4242 4242 4242`, exp `12/34`, CVC `123` → webhook thật qua stripe-listener → đơn PAID. Đây là tiền đề cho A15 (refund thật).
3. Dashboard verification: PaymentIntents/Charges/Transfers/Refunds khớp với DB (`TRANSACTIONS`, `SELLER_TRANSFERS`, `REFUNDS`).
4. Webhook resend: Dashboard → Developers → Webhooks → resend event → verify idempotency (không double-process).

**Phase 4 — Async & negative:**
- Outbox/Debezium: tạo order.paid → assert notification nhận (qua A09 mở rộng).
- Idempotency: gửi trùng webhook/payment.requested → không tạo bản ghi đôi.
- AuthZ: buyer gọi API seller/admin → 403; token hết hạn → 401.
- Saga deadline: verify log schedule payment-timeout/shipping-timeout (👁).

**Phase 5 — Báo cáo:** cập nhật `documents/traceability/` (UC ↔ test ↔ kết quả), tổng hợp pass/fail + bug đã sửa.

## 3. Tiêu chí hoàn thành

1. 100% UC trong `documents/use-cases/` có ít nhất một phương pháp kiểm thử được thực thi và ghi nhận kết quả.
2. Toàn bộ suite tự động xanh trên stack dev compose.
3. Các flow Stripe (onboarding, payment thật, refund thật, transfer) được verify chéo giữa Dashboard và DB.
4. Không xóa/đụng volume dữ liệu nào trong toàn bộ quá trình.
