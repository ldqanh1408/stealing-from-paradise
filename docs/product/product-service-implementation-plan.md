# Kế hoạch Triển khai Product Service Redesign

Dựa vào thiết kế kiến trúc, database và luồng UI mới, hệ thống yêu cầu thiết kế lại toàn bộ từ model/entity đến các business logics phức tạp (Lazy Cart, Concurrency, Kafka Sync).

## Cách tiếp cận & Nguyên tắc
1. **Dữ liệu là nguồn gốc (PostgreSQL + Redis):** Mapping chặt với PostgreSQL, dùng `UUID`, `JSONB` cho `attributes`, `variant_attributes`. Redis giữ `stock:{sku_id}` để chống overselling. Ảnh product/review lưu ở MinIO và chỉ giữ URL.
2. **Transaction & Concurrency:** Update SKU phải kéo theo tính lại `product.status` trong cùng transaction. Lock lạc quan ở SKU (`@Version`) + cơ chế 2 lớp khi đặt hàng (Redis DECRBY → DB UPDATE).
3. **Lazy Cart:** `cart_item` lưu snapshot giá/tên/ảnh; khi GET cart thì so sánh trực tiếp `sku.price` với `cart_item.price_snapshot` và trả về cờ trạng thái, không ghi DB.
4. **Async & Resiliency:** Kafka publish/consume có retry + DLQ; service không bị block khi Kafka chậm.

## Các bước (Phases) thực hiện

### Phase 1: Database & Entity Layer (Nền tảng Data)
1. Setup JPA Entities:
   - `Category` (self-referencing)
   - `Product` (status: `active` | `out_of_stock` | `inactive`)
   - `Sku` (`original_price`, `price_updated_at`, `variant_attributes`, `image_url`, `@Version`)
   - `ProductImage` (sku_id NULL = ảnh chung, sku_id != NULL = ảnh biến thể)
   - `StockReservation` (status: `pending` | `confirmed` | `released`, `expires_at`)
   - `Cart`, `CartItem` (snapshot: `price_snapshot`, `sku_name_snapshot`, `sku_image_snapshot`)
   - `Review`, `ReviewMedia`, `ReviewSummary`
2. Cấu hình Hibernate Type cho `JSONB` (`hypersistence-utils`) và `UUID`.
3. Tạo các tầng Repository (`JpaRepository`) ứng với mỗi Entity + index cần thiết (GIN cho JSONB, unique `cart_id + sku_id`).

### Phase 2: DTO & Mapper Layer
1. DTO chính:
   - `ProductDetailResponse` (product + skus + images + review_summary)
   - `SkuResponse` (id, price, original_price, stock_quantity, status, variant_attributes, image_url, price_updated_at)
   - `CartResponse` / `CartItemResponse` (flags: `has_price_change`, `is_unavailable`, `out_of_stock`, kèm `current_price`)
   - `CheckoutPreviewRequest` / `CheckoutPreviewResponse` (item-level error list)
   - `ReviewResponse` (rating, title, content, media[]) + `ReviewSummaryResponse`
   - `CategoryResponse` (tree hoặc flat list)
2. Dùng MapStruct (hoặc mapper thủ công) để map Entity → DTO, có xử lý transient fields cho Cart.

### Phase 3: Core Business Logic (Service Layer)
1. **CategoryService**: Duyệt cây danh mục, cache Redis; có chiến lược invalidate khi thay đổi.
2. **ProductService / SkuService**:
   - Tạo product với `status = 'inactive'` rồi publish sang `active` khi hoàn tất.
   - Update SKU price/stock -> cập nhật `price_updated_at` + Redis `stock:{sku_id}`.
   - Tính lại `product.status` trong cùng transaction:
     - `active`: có ít nhất 1 SKU `active` và `stock_quantity > 0`
     - `out_of_stock`: tất cả SKU `stock_quantity = 0`
     - `inactive`: seller ẩn sản phẩm
3. **CartService (Lazy)**:
   - `addToCart`: soft-check tồn kho/active; UPSERT `cart_item` và lưu snapshot.
   - `getCart`: batch load SKU, so sánh `sku.price` vs `price_snapshot`, set flags `has_price_change`/`out_of_stock`/`is_unavailable`.
4. **CheckoutService**:
    - `checkoutPreview`: validate strict (status/stock/price).
       - Nếu đã có preview session: trả `409 preview_in_use`.
       - Nếu hợp lệ: tạo `checkout_preview:{customer_id}` với `preview_token`, TTL 10 phút; trả token + `expires_at`.
    - `placeOrder`:
       - Bắt buộc có `preview_token`; nếu key mất hoặc không khớp → `409 preview_expired`.
       - Re-validate (status/stock/price) trước khi lock.
       - 2-layer concurrency
     - Lớp 1: Redis DECRBY (rollback nếu < 0)
     - Lớp 2: DB UPDATE + optimistic lock
     - Tạo `stock_reservation` với TTL
       - Xóa preview key khi place-order hoàn tất (thành công hoặc thất bại).
5. **Reservation & Inventory Jobs**:
   - Job cleanup `stock_reservation` hết hạn: release + hoàn kho DB/Redis.
   - Job reconcile DB → Redis (SET + TTL) để self-heal.
6. **ReviewService**:
   - Create review + review_media; cập nhật `review_summary`.
   - List review theo rating/has_media (paginated) + trả summary.

### Phase 4: Message Queue & Events Integration
1. Producer:
   - `product.created`, `product.updated`, `product.inactive`
   - `sku.price_updated`, `sku.stock_updated`
2. Consumer:
   - `order.confirmed`, `order.failed` từ Order Service
   - Update `stock_reservation` và hoàn kho khi thất bại

### Phase 5: Controller & REST APIs
1. Seller/Admin:
   - `POST /products`
   - `PATCH /products/:id` (name/description/attributes/status)
   - `POST /products/:id/images`, `DELETE /products/:id/images/:image_id`
   - `PATCH /skus/:id` (price/stock/status)
2. Public/Client:
   - `GET /products/:slug`
   - `GET /products/:id/reviews` (rating/has_media/page)
   - `POST /products/:id/reviews`
   - `POST /cart/items`, `PATCH /cart/items/:sku_id`, `DELETE /cart/items/:sku_id`, `GET /cart`
   - `POST /checkout/preview`, `POST /checkout/place-order`, `DELETE /checkout/preview`
   - `GET /categories`
3. Listing/search delegated cho Search Service (không code tại Product Service).

## Kiểm thử (Verification)
1. Concurrency: 2 threads update SKU -> verify `ObjectOptimisticLockingFailureException`.
2. Lazy Cart: giá thay đổi -> `has_price_change = true`, trả giá mới.
3. Checkout Preview: khi lệch giá/stock/status -> trả `409` + item lỗi.
4. Preview session: chỉ 1 session/user, TTL 10 phút; hết hạn -> `preview_expired`.
5. Place-order: revalidate trước khi lock; sai lệch -> `409`.
6. Reservation cleanup: reservation hết TTL -> kho hoàn trả + Redis sync.
7. Reconcile job: Redis mất key -> DB tự đè lại stock.
8. Review Summary: tạo review -> `review_summary` cập nhật đúng.

## Quyết định thiết kế (Decisions)
- `product.status` chỉ gồm: `active`, `out_of_stock`, `inactive`.
- So sánh giá dùng trực tiếp `sku.price` vs `cart_item.price_snapshot`, không có `price_checked_at`.
- Bỏ `helpful_count` và `is_anonymous` trong review để giữ đơn giản.
- `checkout/preview` trả `409 Conflict` kèm item lỗi, yêu cầu reload cart.
- Mỗi user chỉ có 1 preview session, TTL 10 phút; place-order yêu cầu `preview_token` hợp lệ.

## Các vấn đề cần cân nhắc thêm (Further Considerations)
1. Redis drift: mặc định dùng reconcile job (DB là source of truth); cân nhắc thêm Debezium nếu cần realtime sync.
2. Thời điểm lock tồn kho: giữ lock ở bước `place-order` (không lock ở preview) để giảm abandoned reservation.
