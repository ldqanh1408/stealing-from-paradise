# Seller Frontend — Remaining / Deferred Tasks

> Trạng thái tính đến 2026-06-12. Phần đã hoàn thành: hoàn thiện luồng đơn hàng seller
> (UC-ORDER-004/006/008), module hóa (lib `orderActions`/`orderStatus`/`productActions`/
> `productStatus`/`stripeError`, gom modal dùng chung), và bộ kiểm thử Vitest đầy đủ
> (`frontend/shared` 93 tests + `frontend/apps/seller` 84 tests, đều xanh).
>
> File này liệt kê những việc **CHƯA làm** / cố ý hoãn để theo dõi tiếp.

---

## 1. Kiểm thử frontend — phần nâng cao còn thiếu

- [ ] **Coverage report**: chạy `npm test -- --coverage` cho seller app, đặt ngưỡng
      (vd ≥80% cho `src/pages`, `src/components`, `src/lib`) và đưa vào CI gate.
- [ ] **Polling deterministic test** (StripeOnboardingPage): hiện chỉ test banner
      `?from=stripe`. Bổ sung test dùng fake timers xác minh `refetchInterval` 3s **dừng**
      khi `onboardingStatus === 'COMPLETE'`.
- [ ] **SSE stream parsing** (NotificationBell): hiện chỉ test ở tầng store
      (`notificationStore`). Bổ sung test cho nhánh đọc `text/event-stream` (mock `fetch`
      trả stream + `getReader`) — gồm reconnect sau 5s và xử lý 401 → `handleAuthFailure`.
- [ ] **ProductManagementPage**: test debounce ô tìm kiếm (300ms) gửi đúng param `search`,
      và test filter theo `status` tab gọi lại query với param đúng.
- [ ] **TrackingModal/CancelModal**: test nhánh lỗi (API reject) hiển thị message từ
      `err.response.data.message`.

## 2. API/luồng seller chưa được UI dùng nhưng nên phủ

- [ ] `userApi.changePassword` (`POST /users/me/change-password`) — chưa có UI trong
      seller app (SellerSettingsPage chỉ sửa profile). Quyết định: thêm UI đổi mật khẩu
      hay bỏ. Nếu thêm → test kèm.
- [ ] `userApi.getAvatarPresignedUrl` + upload avatar — chưa dùng trong seller settings.
- [ ] `userApi.registerAsSeller` (`POST /users/me/roles/seller`) — nâng cấp BUYER→SELLER
      cho user đã đăng nhập; hiện seller app chỉ có đăng ký mới. Xác nhận có cần luồng này.

## 3. E2E trình duyệt thật (đã hoãn theo quyết định)

- [ ] Playwright E2E (đã chọn **Vitest/jsdom only** ở vòng này). Nếu sau cần:
      chạy app ở chế độ mock (`.env.mock`) hoặc backend thật để kiểm:
      redirect Stripe Connect (start → Stripe → `/stripe/return`), luồng đăng nhập/đăng ký,
      và happy-path đặt vận đơn → RTS/cancel end-to-end.

## 4. Tech-debt / phát hiện trong quá trình làm

- [ ] **Cross-platform deps**: `frontend/apps/seller` và `frontend/shared` hard-code
      `@rollup/rollup-linux-x64-gnu` / `@rolldown/binding-linux-x64-gnu` trong
      `dependencies` → `npm install` thường trên Windows lỗi `EBADPLATFORM`, phải dùng
      `npm install --force`. Cân nhắc chuyển sang `optionalDependencies` đa nền tảng hoặc
      bỏ khỏi `dependencies` để CI/dev đa OS không cần `--force`.
- [ ] **SellerPaymentsPage định dạng tiền**: dùng `fmt = n => (n/100)...` (đơn vị minor của
      Stripe) trong khi Dashboard/đơn hàng dùng VND nguyên. Xác minh backend `getEarnings`
      thực sự trả minor units; nếu không sẽ hiển thị sai 100 lần. (Chưa kiểm chứng — để
      nguyên vì ngoài phạm vi sửa luồng đơn hàng.)
- [ ] **OrderDrawer** chưa hiển thị badge trạng thái (`OrderStatusBadge`) — đồng bộ UI nếu muốn.

## 5. Ranh giới backend (KHÔNG kiểm ở frontend — tham chiếu chéo)

Các hành vi do Stripe webhook (`POST /stripe/webhook`, UC-PAYMENT-003) sinh ra thuộc
`payment-service`, phủ bởi bộ E2E/backend, **không** thuộc frontend:

- [ ] Xác thực `Stripe-Signature` (`Webhook.constructEvent`).
- [ ] Idempotency: dedupe theo `event.id` + check trạng thái (BR-PAYMENT-011).
- [ ] Map event → state: `payment_intent.succeeded/failed`, `charge.refunded`,
      `transfer.created/reversed`, `account.updated` (BR-PAYMENT-004).
- [ ] Phát Kafka (`payment.success/failed`, `refund.stripe_auto`, …) và Saga liên quan.

> Frontend chỉ phản ánh **state kết quả** qua: polling `getStripeStatus`, refetch
> `getEarnings`, và SSE notification — phần này đã được test ở `apps/seller`.

## 6. CI / DX

- [ ] Thêm 2 lệnh test vào pipeline CI: `cd frontend/shared && npm test` và
      `cd frontend/apps/seller && npm test` (+ `tsc --noEmit`).
- [ ] Tài liệu hóa quy ước test trong `frontend/README` hoặc app README (vị trí test theo
      tầng: API/store ở `shared/__tests__`, component/page ở seller app; helper
      `src/test/utils.tsx`; lưu ý `resolve.dedupe` cho react/react-router).
