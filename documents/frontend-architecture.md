# Frontend Architecture — FlashSale / Stealing From Paradise

> Tài liệu chi tiết kiến trúc frontend: 3 ứng dụng (`customer`, `seller`, `admin`)
> + 1 package dùng chung (`shared`), trọng tâm phần **AI Chat Service** đã hoàn thiện.
> Cập nhật: 2026-06-12.

---

## 1. Tổng quan & công nghệ

```
frontend/
├── apps/
│   ├── customer/   # App người mua (sản phẩm, giỏ, đặt hàng, hoàn tiền, AI chat)
│   ├── seller/     # App người bán (sản phẩm, đơn hàng, Stripe, thu nhập)
│   └── admin/      # App quản trị
└── shared/         # API clients, stores, components, utils dùng chung cho cả 3 app
```

| Thành phần | Công nghệ |
|---|---|
| UI | React 19 + TypeScript 5.6, Tailwind CSS 3 |
| Build/dev | Vite 6 (app) / Vite 8 + rolldown (shared, qua vitest 4) |
| State | Zustand 5 (store toàn cục) + TanStack React Query 5 (server cache) |
| HTTP | Axios 1.7 (1 singleton `apiClient` + interceptor refresh token) |
| Routing | react-router-dom 6 (lazy routes + `PrivateRoute` guard) |
| Realtime | SSE qua `fetch` + `ReadableStream` (chat & notification) |
| Test | Vitest 4 + @testing-library/react + jsdom |

Mỗi app là một Vite project độc lập (`node_modules` riêng) và import `shared` qua alias
`@shared` (đường dẫn tương đối `../../shared`). Alias `@` trỏ tới `src` của app.

---

## 2. Tầng `shared`

### 2.1 API clients (`shared/api/*.ts`)
Mỗi domain một file, đều dùng `apiClient` singleton:
`auth`, `user`, `address`, `product`, `category`, `cart`, `order`, `payment`,
`seller`, `refund`, `flashSale`, `notification`, `chat`. Response chuẩn hoá theo
`ApiResponse<T>` (`{ success, message?, data?, errorCode?, timestamp }`) và
`PageResponse<T>` cho danh sách phân trang.

### 2.2 Axios singleton (`shared/lib/axios.ts`)
- Gắn `Authorization: Bearer <accessToken>` (cookie `js-cookie`) cho mọi request
  (trừ mock mode).
- **Interceptor 401 → refresh**: tự gọi `/auth/refresh` (qua axios "raw" để tránh
  loop), hàng đợi `failedQueue` cho các request song song; thất bại → `handleAuthFailure()`
  xoá cookie + chuyển `/login`. Bỏ qua các request `/auth/*` để không reload khi sai mật khẩu.
- `installMockInterceptor` cho **mock mode** (`.env.mock`): chặn request và trả dữ liệu
  giả từ `shared/api/mock.ts` (mọi endpoint, kèm độ trễ giả lập) — dùng để chạy UI không cần backend.

### 2.3 Stores Zustand (`shared/store/*.ts`)
`authStore` (login/register/registerSeller + decode JWT role, persist `sessionStorage`),
`cartStore`, `orderStore`, `productStore`, `paymentStore`, `sellerStore`, `refundStore`,
`flashSaleStore`, `loyaltyStore`, `searchStore`, `addressStore`, `notificationStore`, `chatStore`.

### 2.4 Components & utils dùng chung
`Layout`, `Navbar`, `Footer`, `PrivateRoute` (guard theo `role`), `NotificationBell`
(SSE), `Pagination`, `StatusBadge`, `EmptyState`, `ErrorBoundary`, `ConfirmDialog`,
`FilterChip`. Utils: `format.ts` (`fmtVnd`, `fmtDate`, `fmtDateTime`).

---

## 3. App `customer`

Routes (lazy, [App.tsx](../frontend/apps/customer/src/App.tsx)): `/products`, `/products/:id`,
`/flash-sales`, `/cart`, `/checkout` → `/checkout/payment` (Stripe) → `/checkout/result`,
`/orders`, `/orders/:parentOrderId`, `/refunds`, `/profile`, `/addresses`, `/account-settings`.
Trang công khai không cần đăng nhập; phần còn lại bọc `PrivateRoute`.
`<ChatWidget />` được mount toàn cục khi `isAuthenticated`.

---

## 4. App `seller`

Routes: `/dashboard`, `/products`, `/orders`, `/orders/:orderId`, `/payments`,
`/stripe-onboarding` (+ `/stripe/return`, `/stripe/refresh`), `/settings`. Guard `role="SELLER"`.
Luồng đơn hàng đã chuẩn hoá theo use case (UC-ORDER-004/006/008) với các lib thuần
`lib/orderActions.ts`, `lib/orderStatus.tsx` và modal dùng chung
(`TrackingModal`, `CancelOrderModal`, `RTSModal`). Sản phẩm: `lib/productActions.ts`,
`lib/productStatus.tsx`. Stripe: `lib/stripeError.ts`.

> Phản ánh **sự kiện Stripe** ở frontend: webhook đi vào backend (`POST /stripe/webhook`),
> seller UI phản ánh state kết quả qua polling `getStripeStatus`, refetch `getEarnings`,
> và SSE notification. Chi tiết: xem `seller-frontend-remaining-tasks.md`.

---

## 5. AI Chat Service (frontend) — chi tiết

### 5.1 Kiến trúc 3 lớp

```
ChatWidget.tsx (UI, customer app)
      │  đọc/điều khiển
      ▼
chatStore.ts (Zustand: messages, streaming, pendingConfirmation, toolStatus...)
      │  gọi
      ▼
chat.api.ts (REST: sessions/history/suggest/confirm)  +  streamChat() (SSE POST /chat)
      │
      ▼
ai-chat-service (port 8093, base URL = VITE_API_URL bỏ '/v1' + '/ai')
```

`getChatBaseUrl()` đổi base `.../api/v1` → `.../api/ai` vì AI service không nằm dưới `/v1`.

### 5.2 REST endpoints (`chat.api.ts`)
| Hàm | HTTP | Mục đích |
|---|---|---|
| `createSession()` | `POST /sessions` | Tạo phiên (UC-001) |
| `closeSession(id)` | `DELETE /sessions/{id}` | Đóng phiên |
| `getHistory(id,{pageSize,before})` | `GET /chat/history` | Lịch sử (cursor) |
| `getSuggestions()` | `GET /suggest` | Gợi ý nhanh cá nhân hoá |
| `confirmAction(confirmId, confirmed, sessionId?)` | `POST /confirm` | Xác nhận/từ chối Mức 3 (UC-003) |

> `confirmAction` gửi cả `confirmed` (boolean cũ) **và** `decision: CONFIRMED|REJECTED`
> + `sessionId` để khớp đúng hợp đồng UC-AICHAT-003 (`{confirmId, sessionId, decision}`).

### 5.3 Giao thức SSE — `streamChat(sessionId, message, callbacks)`
Mở `POST /chat` với `Accept: text/event-stream`, đọc `ReadableStream`, parse từng dòng
`event:` / `data:` và phân phối qua callbacks:

| SSE event | Callback | Xử lý ở store |
|---|---|---|
| `delta` | `onDelta(text)` | Nối token vào message ASSISTANT cuối; xoá `toolStatus` |
| `tool_start` / `tool_call` | `onEvent` | Đặt `toolStatus` = "Đang tra cứu…" (UC-002 b8) |
| `products` | `onEvent` | Gắn mảng sản phẩm vào message để render card |
| `confirmation_required` | `onEvent` | Đặt `pendingConfirmation` → render thẻ xác nhận Mức 3 |
| `done` | `onDone` | `isStreaming=false`, clear `toolStatus` |
| `error` | `onError` | Hiển thị lỗi; nếu 422/expired → reset `currentSessionId` |

Trả về `AbortController` để **huỷ stream** (nút "Dừng AI trả lời" → `cancelStreaming`).
**Mock mode**: `streamChat` mô phỏng streaming theo từ khoá ("hoàn tiền" → confirmation,
"sản phẩm" → products) để demo không cần LLM.

### 5.4 `chatStore` — state máy
State chính: `currentSessionId`, `messages[]` (kèm `products?`), `isStreaming`,
`toolStatus`, `pendingConfirmation`, `suggestions[]`, `isOpen`, `error`, `abortController`.

Hành vi đáng chú ý (đã hoàn thiện theo UC):
- `createSession()` đặt `currentSessionId` + seed lời chào ASSISTANT (UC-001 main flow).
- `sendMessage()` tự tạo session nếu chưa có (UC-001 A1), thêm USER + ASSISTANT rỗng,
  mở SSE, gom `delta`.
- `onError` với `422`/`expired` → xoá `currentSessionId` để tin nhắn kế tiếp tạo phiên
  mới (UC-001 A2 / UC-002 422).
- `confirmAction`/`rejectAction` → `POST /confirm` (kèm `sessionId`) rồi `fetchHistory`
  để lấy message kết quả; ẩn thẻ xác nhận.

### 5.5 `ChatWidget` — UI
- **Launcher** nổi góc phải; mở ra panel 400×550.
- **Mở panel** → `fetchSuggestions()` + `createSession()` nếu chưa có (UC-001).
- Render bong bóng USER/ASSISTANT, **card sản phẩm**, chỉ báo **typing** và **tool lookup**
  ("Đang tra cứu…").
- **Thẻ xác nhận Mức 3**: hiển thị tóm tắt + mã đơn, **đếm ngược 5 phút** (UC-003 A5);
  hết giờ → hiện "Hết thời gian xác nhận" và **vô hiệu hoá** nút Đồng ý/Từ chối.
- **Suggestion chips**, ô nhập (khoá khi đang stream / đang chờ xác nhận), nút **Dừng AI**.

### 5.6 Bản đồ Use Case ↔ frontend
| UC | Frontend |
|---|---|
| UC-AICHAT-001 Start session | `createSession` + open effect + greeting + `getSuggestions` |
| UC-AICHAT-002 Send message (stream) | `sendMessage` + `streamChat` + delta/tool/products/done; A2 confirmation; 422 reset |
| UC-AICHAT-003 Confirm/Reject | thẻ xác nhận + `confirmAction(decision)` + đếm ngược A5 |

Ngoài phạm vi frontend (backend `ai-chat-service`): gọi LLM/tool, ghi `CHAT_MESSAGES`/
`TOOL_CALL_LOGS`, phát Kafka (`ai.session.created`, `ai.chat.message_received`,
`ai_chat.confirmation_resolved`), rate-limit, MongoDB pending-confirmation.

---

## 6. Auth & bảo vệ route
`authStore.login/register/registerSeller` lưu `accessToken`/`refreshToken` (cookie),
decode JWT lấy `role`. `PrivateRoute` chặn chưa đăng nhập → `/login`, sai role → `/`.
Refresh token tự động ở interceptor; thất bại → đăng xuất + về `/login`.

## 7. Notifications (SSE)
`NotificationBell` mở stream `GET /notifications/stream` (Bearer token), parse `data:` →
`notificationStore.addNotification` (dedupe theo id, tăng unread). Reconnect sau 5s; 401 →
`handleAuthFailure`. Mock mode: sinh thông báo định kỳ.

---

## 8. Kiểm thử (Vitest)

| Vị trí | Môi trường | Nội dung | Lệnh |
|---|---|---|---|
| `shared/__tests__/` | node (jsdom khi cần) | API contract (`vi.mock('../lib/axios')`), store (`authStore`, `notificationStore`, `chatStore`) | `cd frontend/shared && npm test` |
| `apps/seller/` | jsdom | lib + component + page (order/product/stripe/payments…) | `cd frontend/apps/seller && npm test` |
| `apps/customer/` | jsdom | `ChatWidget` (+ mở rộng dần) | `cd frontend/apps/customer && npm test` |

Helper render: `src/test/utils.tsx` (`renderWithProviders` = QueryClient + MemoryRouter).

**Lưu ý Windows / cấu hình:**
- Cài deps cần `npm install --force` (apps hard-code `@rollup/rollup-linux-x64-gnu`,
  shared dùng `rolldown` → thiếu binary win32). Xem `seller-frontend-remaining-tasks.md`.
- `vitest.config.ts` mỗi app PHẢI có `resolve.dedupe: ['react','react-dom','react-router',
  'react-router-dom','@tanstack/react-query']` — nếu không, test render `@shared/components/*`
  sẽ lỗi "Cannot read properties of null (reading 'useContext')" do trùng React.

---

## 9. Build & môi trường
- Dev: `npm run dev` (Vite, seller port 3001), proxy `/api` → `VITE_PROXY_TARGET`.
- Build: `npm run build` (`tsc && vite build`).
- Biến môi trường chính: `VITE_API_URL` (mặc định `http://localhost:8080/api/v1`),
  `VITE_PROXY_TARGET`, cờ mock mode (`.env.mock`).
