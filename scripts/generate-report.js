const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  HeadingLevel, AlignmentType, BorderStyle, WidthType, ShadingType,
  VerticalAlign, LevelFormat, PageNumber, PageBreak, TabStopType, TabStopPosition
} = require('docx');
const fs = require('fs');

const BORDER = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
const BORDERS = { top: BORDER, bottom: BORDER, left: BORDER, right: BORDER };
const HEADER_COLOR = "1E4D8C";
const ROW_ALT = "EEF3FB";
const PAGE_WIDTH = 12240;
const MARGIN = 1080;
const CONTENT_W = PAGE_WIDTH - MARGIN * 2;

function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 360, after: 180 },
    children: [new TextRun({ text, bold: true, size: 36, font: "Arial", color: HEADER_COLOR })]
  });
}

function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 240, after: 120 },
    children: [new TextRun({ text, bold: true, size: 28, font: "Arial", color: "2E5FA3" })]
  });
}

function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 160, after: 80 },
    children: [new TextRun({ text, bold: true, size: 24, font: "Arial", color: "3A6CC4" })]
  });
}

function para(text, opts = {}) {
  return new Paragraph({
    spacing: { before: 60, after: 60 },
    children: [new TextRun({ text, size: 22, font: "Arial", ...opts })]
  });
}

function bullet(text) {
  return new Paragraph({
    numbering: { reference: "bullets", level: 0 },
    spacing: { before: 40, after: 40 },
    children: [new TextRun({ text, size: 22, font: "Arial" })]
  });
}

function divider() {
  return new Paragraph({
    spacing: { before: 120, after: 120 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: "CCCCCC", space: 1 } },
    children: []
  });
}

function makeTable(headers, rows, colWidths) {
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: [
      new TableRow({
        tableHeader: true,
        children: headers.map((h, i) =>
          new TableCell({
            borders: BORDERS,
            width: { size: colWidths[i], type: WidthType.DXA },
            shading: { fill: HEADER_COLOR, type: ShadingType.CLEAR },
            margins: { top: 60, bottom: 60, left: 100, right: 100 },
            children: [new Paragraph({
              children: [new TextRun({ text: String(h), size: 18, font: "Arial", bold: true, color: "FFFFFF" })]
            })]
          })
        )
      }),
      ...rows.map((row, ri) =>
        new TableRow({
          children: row.map((cell, ci) => new TableCell({
            borders: BORDERS,
            width: { size: colWidths[ci], type: WidthType.DXA },
            shading: { fill: ri % 2 === 1 ? ROW_ALT : "FFFFFF", type: ShadingType.CLEAR },
            margins: { top: 60, bottom: 60, left: 100, right: 100 },
            children: [new Paragraph({
              children: [new TextRun({ text: String(cell || ''), size: 18, font: "Arial", color: "333333" })]
            })]
          }))
        })
      )
    ]
  });
}

function sp(n = 1) {
  return new Paragraph({ spacing: { before: 0, after: n * 80 }, children: [] });
}

// ─── BUILD DOC ───────────────────────────────────────────────────
const children = [];

// TITLE PAGE
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 1440, after: 240 },
  children: [new TextRun({ text: "FlashSale E-Commerce", bold: true, size: 56, font: "Arial", color: HEADER_COLOR })]
}));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 0, after: 120 },
  children: [new TextRun({ text: "Project Report", size: 36, font: "Arial", color: "2E5FA3" })]
}));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 0, after: 80 },
  children: [new TextRun({ text: "Generated: 2026-05-12  |  Version: 3.0  |  Microservices: 12 services + 3 frontends", size: 22, font: "Arial", color: "666666" })]
}));
children.push(new Paragraph({ children: [new PageBreak()] }));

// MỤC LỤC
children.push(h1("Mục lục"));
["1. Tổng quan","2. Kiến trúc hệ thống","3. Cơ sở dữ liệu","4. API","5. Functional Requirements","6. Business Rules","7. Use Cases","8. State Machines","9. Kafka Events","10. Infrastructure & Operations"].forEach(item => children.push(bullet(item)));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ─── 1. TỔNG QUAN ───────────────────────────────────────────────
children.push(h1("1. Tổng quan"));
children.push(para("FlashSale là nền tảng thương mại điện tử đa người bán với 3 vai trò: Buyer, Seller, Admin."));
children.push(sp());
children.push(makeTable(
  ["Chỉ số", "Giá trị"],
  [["Backend Microservices","12 (4 Axon CQRS + 8 traditional)"],["Frontend Apps","3 (Customer :3000, Seller :3001, Admin :3002)"],["Functional Requirements","107"],["API Endpoints","60+"],["Kafka Topics","58 (44 event + 14 request-reply)"],["Database Tables","25+ bảng PostgreSQL"],["Cronjobs","16 (1 implemented, 15 post-MVP)"]],
  [5040, 5040]
));
children.push(sp());
children.push(divider());

// ─── 2. KIẾN TRÚC ───────────────────────────────────────────────
children.push(h1("2. Kiến trúc hệ thống"));
children.push(h2("2.1 Sơ đồ tổng thể"));
children.push(new Paragraph({
  spacing: { before: 60, after: 60 },
  children: [new TextRun({
    text: "[Nginx :80]\n    ├── Customer App :3000\n    ├── Seller App :3001\n    └── Admin App :3002\n            │\n    [API Gateway :8080]\n    ├── Discovery Service :8761\n    ├── Eureka Dashboard\n    └── Axon Server\n\nidentity :8081   payment :8082   refund :8094\norder    :8083   flashsale:8085  product :8090\nsearch   :8091   notif   :8092   chat    :8093",
    font: "Courier New", size: 18, color: "333333"
  })]
}));
children.push(sp());

children.push(h2("2.2 Danh sách dịch vụ"));
children.push(makeTable(
  ["#","Service","Port","DB","Pattern","Trách nhiệm"],
  [["1","api-gateway","8080","—","Gateway","JWT, routing, rate limiting"],["2","discovery-service","8761","—","Eureka","Service registry"],["3","identity-service","8081","PostgreSQL","JPA","Auth, users, addresses"],["4","payment-service","8082","PostgreSQL","Axon CQRS","Stripe Connect, payout"],["5","refund-service","8094","PostgreSQL","Axon CQRS","Refund, admin review, RTS"],["6","order-service","8083","PostgreSQL","Axon CQRS+Saga","Checkout, order lifecycle"],["7","flashsale-service","8085","PostgreSQL+Redis","Axon CQRS","Flash sale, Redis Lua"],["8","product-service","8090","PostgreSQL","JPA","Catalog, cart, images"],["9","search-service","8091","Elasticsearch","Traditional","Full-text search"],["10","notification-service","8092","MongoDB","Traditional","SSE notifications"],["11","chat-service","8093","MongoDB","Traditional","AI chat"],["12","dev-data-runner","—","—","CLI","Seed data"]],
  [600, 1800, 700, 1500, 1500, 3980]
));
children.push(sp());

children.push(h2("2.3 Infrastructure"));
children.push(makeTable(
  ["Component","Version","Port","Used by"],
  [["PostgreSQL","15.4","5432","identity, payment, refund, order, flashsale, product"],["MongoDB","6.0","27017","notification, chat"],["Redis","7.2","6379","flashsale, identity, gateway"],["Elasticsearch","8.10","9200","search"],["MinIO","latest","9000/9001","product"],["Kafka","7.4.0","9092","all services"],["Axon Server","latest","8024/8124","payment, refund, order, flashsale"]],
  [2000, 1200, 1200, 5680]
));
children.push(sp());

children.push(h2("2.4 Tech Stack"));
children.push(para("Java 25 · Spring Boot 4.0.4 · Spring Cloud 2025.1.1 · Axon 4.13.0 · React 19 · Vite 6 · TypeScript · Tailwind CSS · Docker · GitHub Actions"));
children.push(divider());

// ─── 3. DATABASE ────────────────────────────────────────────────
children.push(h1("3. Cơ sở dữ liệu"));

const dbSections = [
  {title:"3.1 identity-service — PostgreSQL (public)",tables:[["users","id BIGSERIAL PK, username VARCHAR UNIQUE, email VARCHAR UNIQUE, phone VARCHAR UNIQUE, password VARCHAR (Bcrypt), full_name VARCHAR, status VARCHAR (ACTIVE/LOCKED), role VARCHAR (BUYER/SELLER/ADMIN), version INT, created_at, updated_at"],["roles","id BIGSERIAL PK, user_id FK→users, role_name VARCHAR"],["customers / sellers / admins","id BIGSERIAL PK, user_id FK→users UNIQUE"],["addresses","id BIGSERIAL PK, user_id FK→users, province_id INT, district_id INT, full_address TEXT, is_default BOOLEAN"]]},
  {title:"3.2 product-service — PostgreSQL (public)",tables:[["category","id UUID PK, parent_id FK→self, name VARCHAR, slug VARCHAR UNIQUE, description TEXT, image_url TEXT, sort_order INT, is_active BOOLEAN"],["product","id UUID PK, category_id FK, seller_id UUID, name VARCHAR(500), slug VARCHAR(500) UNIQUE, description TEXT, attributes JSONB, status VARCHAR (draft/pending/approved/rejected/active/out_of_stock/inactive), reject_reason TEXT, reviewed_at TIMESTAMP, reviewed_by BIGINT, reject_count INT"],["product_variant","id UUID PK, product_id FK, variant_code VARCHAR UNIQUE, variant_name VARCHAR, variant_attributes JSONB, price DECIMAL, original_price DECIMAL, stock_quantity INT, status VARCHAR, version INT, image_url TEXT"],["product_image","id UUID PK, product_id FK, variant_id FK NULLABLE, url TEXT (MinIO), sort_order INT"],["stock_reservation","id UUID PK, variant_id FK, session_id VARCHAR, quantity INT, status VARCHAR (pending/confirmed/released), expires_at TIMESTAMP"],["cart","id UUID PK, customer_id UUID UNIQUE"],["cart_item","id UUID PK, cart_id FK, variant_id FK, quantity INT, price_snapshot DECIMAL, UNIQUE(cart_id, variant_id)"]]},
  {title:"3.3 order-service — PostgreSQL (public)",tables:[["parent_orders","id BIGSERIAL PK, customer_id BIGINT, session_id VARCHAR UNIQUE, total_amt DECIMAL, final_amt DECIMAL, currency VARCHAR DEFAULT 'VND', status VARCHAR (PENDING_PAYMENT/PAID/CANCELLED)"],["orders","id BIGSERIAL PK, parent_order_id FK, seller_id BIGINT, order_code VARCHAR UNIQUE, total_amt DECIMAL, final_amt DECIMAL, net_payout_amount DECIMAL, status VARCHAR, shipping_address JSONB, tracking_number VARCHAR, carrier VARCHAR, return_window_end TIMESTAMP, shipped_at, delivered_at, paid_at"],["order_items","id BIGSERIAL PK, order_id FK, variant_id FK, sku_code VARCHAR, name_snapshot VARCHAR, price_snapshot DECIMAL, quantity INT, fs_item_id FK NULLABLE"]]},
  {title:"3.4 payment-service — PostgreSQL (payment)",tables:[["transactions","id BIGSERIAL PK, parent_order_id BIGINT, amount DECIMAL, trans_ref VARCHAR UNIQUE, application_fee_amount DECIMAL, stripe_connect_mode VARCHAR, status VARCHAR, raw_response JSONB, pay_at TIMESTAMP"],["seller_stripe_accounts","id BIGSERIAL PK, seller_id BIGINT UNIQUE, stripe_account_id VARCHAR UNIQUE, account_status VARCHAR, charges_enabled BOOLEAN, payouts_enabled BOOLEAN, onboarding_url TEXT"],["seller_transfers","id BIGSERIAL PK, order_id FK, seller_id BIGINT, transfer_amount DECIMAL, refunded_amount DECIMAL, stripe_transfer_id VARCHAR, payout_eligible_at TIMESTAMP, platform_commission_amt DECIMAL, status VARCHAR (PENDING→AWAITING_DELIVERY→RETURN_WINDOW→READY_FOR_PAYOUT→PAID_OUT/REFUNDED/REVERSED)"]]},
  {title:"3.5 refund-service — PostgreSQL (refund)",tables:[["refunds","id BIGSERIAL PK, transaction_id BIGINT, order_id BIGINT, user_id BIGINT, group_ref UUID, type VARCHAR (FULL/PARTIAL), initiated_by VARCHAR, refund_reason_type VARCHAR, amount DECIMAL, reason TEXT, status VARCHAR (PENDING/SUCCESS/FAILED/REJECTED), evidence_images JSONB, reject_reason TEXT, admin_note TEXT, reviewed_by BIGINT, reviewed_at TIMESTAMP, refund_ref VARCHAR, raw_response JSONB"],["refund_items","id BIGSERIAL PK, refund_id BIGINT, item_id BIGINT, quantity INT, refund_amount DECIMAL, item_reason TEXT, status VARCHAR, return_tracking_number VARCHAR, returned_at TIMESTAMP"],["Axon tables","token_entry, saga_entry, association_value_entry"]]},
  {title:"3.6 flashsale-service — PostgreSQL (public)",tables:[["fs_sessions","id BIGSERIAL PK, name VARCHAR, start_time TIMESTAMP, end_time TIMESTAMP, registration_deadline TIMESTAMP, discount_percentage DECIMAL, status VARCHAR (UPCOMING/ACTIVE/ENDED)"],["fs_items","id BIGSERIAL PK, session_id FK, product_id UUID, discount_applied DECIMAL, UNIQUE(session_id, product_id)"],["fs_reminders","id BIGSERIAL PK, customer_id FK, session_id FK"]]},
  {title:"3.7 notification-service — MongoDB",tables:[["notifications","_id, user_id Int64, title String, body String, type String, metadata Object, is_read Boolean, read_at DateTime, created_at DateTime"]]},
  {title:"3.8 chat-service — MongoDB",tables:[["Collections","chat_sessions, chat_messages, pending_confirmations, tool_call_logs"]]},
  {title:"3.9 search-service — Elasticsearch (index: skus)",tables:[["SKU index","SKU-first với field collapse theo product_id. Fields: sku_id, product_id, seller_id, product_name (VN analyzer), price, original_price, has_discount, stock_status, category_id, variant_attributes..."]]}
];

dbSections.forEach(sec => {
  children.push(h2(sec.title));
  children.push(makeTable(["Bảng / Collection","Cấu trúc"], sec.tables, [2200, 7880]));
  children.push(sp());
});
children.push(divider());

// ─── 4. API ─────────────────────────────────────────────────────
children.push(h1("4. API"));
children.push(para("Base URL: /api/v1 → Gateway stripPrefix(1) → microservice"));
children.push(sp());

const apiSections = [
  {title:"4.1 identity-service (8081)",rows:[["POST","/auth/register","Public","Đăng ký"],["POST","/auth/login","Public","Đăng nhập → JWT"],["POST","/auth/refresh","Public","Refresh token"],["POST","/auth/logout","JWT","Thu hồi token"],["GET","/users/me","JWT","Profile"],["PUT","/users/me","JWT","Cập nhật profile"],["GET/POST/PUT/DELETE","/users/me/addresses[/{id}]","JWT","CRUD địa chỉ"],["GET","/admin/users","ADMIN","List users"]]},
  {title:"4.2 product-service (8090)",rows:[["GET","/products, /products/{id}","Public","Catalog"],["GET","/categories, /categories/{id}","Public","Danh mục"],["GET/POST/PUT/DELETE","/cart, /cart/items[/{id}]","JWT","Giỏ hàng"],["POST/PUT/DELETE","/products[/{id}]","SELLER","CRUD sản phẩm"],["POST/PUT/DELETE","/seller/products/{id}/variants, /seller/variants/{id}","SELLER","CRUD variant"],["POST","/admin/products/{id}/approve, /reject","ADMIN","Duyệt/từ chối"]]},
  {title:"4.3 order-service (8083)",rows:[["POST","/orders/checkout","BUYER","Tạo đơn"],["GET","/orders, /orders/{id}","BUYER","Xem đơn"],["POST","/orders/{id}/cancel","BUYER","Huỷ đơn"],["POST","/orders/{id}/confirm-received","BUYER","Nhận hàng"],["POST","/orders/{id}/refund, /orders/parent/{id}/refund","BUYER","Yêu cầu hoàn tiền"],["GET","/sellers/orders, /sellers/orders/{id}","SELLER","Đơn shop"],["PUT","/sellers/orders/{id}/ship","SELLER","Gửi hàng"],["POST","/sellers/orders/{id}/return","SELLER","RTS"]]},
  {title:"4.4 payment-service (8082)",rows:[["GET","/payments/parent-order/{id}","JWT","Thông tin giao dịch"],["POST","/stripe/webhooks","Stripe-Sig","Webhook"],["POST/GET","/stripe/onboarding/*","SELLER","Stripe Connect"],["GET","/seller/payments/*","SELLER","Lịch sử + doanh thu"]]},
  {title:"4.5 refund-service (8094)",rows:[["GET","/admin/refunds, /admin/refunds/{id}","ADMIN","List + detail"],["POST","/admin/refunds/{id}/approve","ADMIN","Duyệt"],["POST","/admin/refunds/{id}/reject","ADMIN","Từ chối"]]},
  {title:"4.6 flashsale-service (8085)",rows:[["GET","/flash-sales, /flash-sales/{id}","Public","Xem phiên"],["POST","/flash-sales/{id}/buy","JWT","Mua (Redis Lua)"],["POST/PUT/DELETE","/flash-sales[/{id}]","SELLER/ADMIN","CRUD phiên"]]},
  {title:"4.7 search-service (8091)",rows:[["GET","/search?q=&category=&min_price=&max_price=","Public","Full-text search"]]},
  {title:"4.8 notification-service (8092)",rows:[["GET","/notifications","JWT","SSE stream"],["PUT","/notifications/{id}/read","JWT","Đánh dấu đọc"]]},
  {title:"4.9 chat-service (8093)",rows:[["POST","/ai/chat/messages","JWT","SSE streaming AI"]]}
];

apiSections.forEach(sec => { children.push(h2(sec.title)); children.push(makeTable(["Method","Endpoint","Auth","Mô tả"], sec.rows, [1500, 3580, 1000, 4000])); children.push(sp()); });
children.push(divider());

// ─── 5. FUNCTIONAL REQUIREMENTS ─────────────────────────────────
children.push(h1("5. Functional Requirements"));
children.push(para("Tổng cộng 107 FRs — nguồn: documents/srs/fr/"));
children.push(sp());

children.push(h2("5.1 identity-service (FR-IDENTITY-001 ~ 015)"));
children.push(makeTable(["FR ID","Requirement","Priority"],[["FR-IDENTITY-001","Public registration với username/email/phone/password/full_name qua POST /auth/register","HIGH"],["FR-IDENTITY-002","Hash password bằng bcrypt, KHÔNG lưu plaintext","HIGH"],["FR-IDENTITY-003","Login trả về JWT access_token (24h) + refresh_token (7d) qua POST /auth/login","HIGH"],["FR-IDENTITY-004","Refresh token có rotated refresh_token qua POST /auth/refresh","HIGH"],["FR-IDENTITY-005","Logout thu hồi token: add JTI vào Redis blocklist với TTL = remaining lifetime","HIGH"],["FR-IDENTITY-006","GET /users/me trả về profile (id, username, email, phone, full_name, status, created_at)","HIGH"],["FR-IDENTITY-007","PUT /users/me cập nhật full_name và/hoặc phone, enforce phone uniqueness","MEDIUM"],["FR-IDENTITY-008","POST /users/me/change-password đổi mật khẩu, yêu cầu old_password","MEDIUM"],["FR-IDENTITY-009","CRUD địa chỉ qua /users/me/addresses với default-address enforcement","MEDIUM"],["FR-IDENTITY-010","Public registration cho SELLER qua POST /auth/register/seller","HIGH"],["FR-IDENTITY-011","GET /admin/users — admin list users với filter (status, role, query)","MEDIUM"],["FR-IDENTITY-012","POST /admin/users/{id}/lock — admin khoá user, thu hồi toàn bộ token","HIGH"],["FR-IDENTITY-013","POST /admin/users/{id}/unlock — admin mở khoá user","HIGH"],["FR-IDENTITY-014","Publish Kafka events cho account lifecycle (post-MVP)","MEDIUM"],["FR-IDENTITY-015","Kafka request-reply order.address — cung cấp shipping address cho Order Service","MEDIUM"]], [2000, 6580, 1500]));
children.push(sp());

children.push(h2("5.2 product-service — Catalog (FR-PRODUCT-001 ~ 015)"));
children.push(makeTable(["FR ID","Requirement","Priority"],[["FR-PRODUCT-001","Browse category tree dạng nested JSON, chỉ is_active=TRUE cho public","HIGH"],["FR-PRODUCT-002","Admin tạo category với unique slug, validate parent_id nếu có","HIGH"],["FR-PRODUCT-003","Admin cập nhật category, emit category.updated Kafka event, chống circular parent","MEDIUM"],["FR-PRODUCT-004","Seller tạo product: name 5-200 chars, desc max 10000, category leaf node, ảnh 1-10 JPEG/PNG/WebP","HIGH"],["FR-PRODUCT-005","List/search products: filter category/status/seller, sort price/date, pagination. Chỉ active+out_of_stock hiển thị public","HIGH"],["FR-PRODUCT-006","Product detail: full variants (active/out_of_stock), images (sort_order), category breadcrumb","HIGH"],["FR-PRODUCT-007","Seller cập nhật product (name, desc, attributes, category), emit product.updated","HIGH"],["FR-PRODUCT-008","Xoá product: 409 nếu variant có active stock reservation. Emit product.deleted","MEDIUM"],["FR-PRODUCT-009","Thêm variant: variant_code 3-50 chars alphanumeric+dash, price >0, unique code","HIGH"],["FR-PRODUCT-010","Cập nhật variant: name, price, status, ảnh. Trigger product status recompute. Emit event","HIGH"],["FR-PRODUCT-011","Cập nhật stock: optimistic locking, stock >= 0, auto-change variant status (out_of_stock/active)","HIGH"],["FR-PRODUCT-012","Upload ảnh qua MinIO presigned URL (15ph TTL), max 10 ảnh, JPEG/PNG/WebP","HIGH"],["FR-PRODUCT-013","Xoá image record (lazy MinIO cleanup)","LOW"],["FR-PRODUCT-014","Reserve stock khi checkout: giữ 15 phút, nếu không confirmed → release","HIGH"],["FR-PRODUCT-015","Release expired stock reservations qua scheduled cleanup job","MEDIUM"]], [2000, 6580, 1500]));
children.push(sp());

children.push(h2("5.3 product-service — Cart (FR-PRODUCT-016 ~ 022)"));
children.push(makeTable(["FR ID","Requirement","Priority"],[["FR-PRODUCT-016","Get cart grouped by seller, enrich real-time variant data, flag price_changed/out_of_stock","HIGH"],["FR-PRODUCT-017","Add item: UPSERT behavior, snapshot giá/tên/ảnh, validate stock, emit cart.item_added","HIGH"],["FR-PRODUCT-018","Update quantity với stock validation","HIGH"],["FR-PRODUCT-019","Remove single item khỏi cart","HIGH"],["FR-PRODUCT-020","Clear toàn bộ cart (cart record vẫn tồn tại)","MEDIUM"],["FR-PRODUCT-021","Cart integrity validation khi checkout preview: price/stock/status check","HIGH"],["FR-PRODUCT-022","Event-driven cart cleanup (checkout completed, flash expired, order cancelled)","MEDIUM"]], [2000, 6580, 1500]));
children.push(sp());

children.push(h2("5.4 product-service — UI Logic (FR-PRODUCT-UI-001 ~ 009)"));
children.push(makeTable(["FR ID","Requirement"],[["FR-PRODUCT-UI-001","Product card display: Thumbnail từ product_image, giá MIN(price), badge Hết hàng/SALE/Bán chạy"],["FR-PRODUCT-UI-002","Image gallery variant-aware: ảnh chung mặc định, swap sang ảnh variant khi chọn, sort by sort_order ASC"],["FR-PRODUCT-UI-003","Price display: giá hiện tại + original_price gạch ngang + discount %, Từ Xđ nếu multiple variants"],["FR-PRODUCT-UI-004","Variant selection matrix: Grouped by attribute keys, disable out-of-stock variants"],["FR-PRODUCT-UI-005","Info tabs: Tab 1 - Specifications từ attributes JSONB. Tab 2 - Rich-text description"],["FR-PRODUCT-UI-006","Quantity selector: Min 1, max = stock_quantity, disable nếu out_of_stock, hiển thị Chỉ còn N sản phẩm"],["FR-PRODUCT-UI-007","Cart item display: Cảnh báo nếu giá thay đổi, hết hàng, hoặc variant bị gỡ"],["FR-PRODUCT-UI-008","Checkout preview: Validate giỏ hàng (stock/giá/trạng thái), generate preview token, confirm place-order"],["FR-PRODUCT-UI-009","Image source matrix: Homepage 300x300, Detail 800x800, Cart 80x80, MinIO URL transformation"]], [2000, 8080]));
children.push(sp());

children.push(h2("5.5 order-service (FR-ORDER-001 ~ 018)"));
children.push(makeTable(["FR ID","Requirement","Priority"],[["FR-ORDER-001","Multi-vendor checkout: 1 parent_order + N sub-orders (theo seller), atomic transaction","P0"],["FR-ORDER-002","Stock validation qua Kafka request-reply với Product Service","P0"],["FR-ORDER-003","Address validation qua Kafka request-reply với Identity Service","P1"],["FR-ORDER-004","Unique order_code format OR-YYYYMMDD-{id}","P1"],["FR-ORDER-005","Buyer paginated order listing với status/date filters","P0"],["FR-ORDER-006","Order detail với items, shipping, refund info","P0"],["FR-ORDER-007","Parent order detail với tất cả sub-orders + payment summary","P1"],["FR-ORDER-008","Buyer huỷ order (PENDING hoặc PAID) với reason","P0"],["FR-ORDER-009","Seller cập nhật tracking number → SHIPPING","P0"],["FR-ORDER-010","Buyer confirm delivery → DELIVERED","P0"],["FR-ORDER-011","Auto-confirm delivery sau 7 ngày nếu buyer không thao tác (JOB-22)","P1"],["FR-ORDER-012","Seller RTS → tự động full refund không cần admin","P0"],["FR-ORDER-013","Buyer refund request (partial/full) trong return window","P0"],["FR-ORDER-014","Seller paginated order listing với filters","P0"],["FR-ORDER-015","Seller dashboard: order counts by status + revenue + pending payouts","P1"],["FR-ORDER-016","Produce Kafka events cho tất cả state transitions","P0"],["FR-ORDER-017","Consume Kafka events: payment.success, payment.failed, refund.*","P0"],["FR-ORDER-018","Axon Saga orchestration: ParentOrderPaymentSaga, timeout 30ph/10ph flash sale","P0"]], [2000, 6580, 1500]));
children.push(sp());

children.push(h2("5.6 payment-service (FR-PAYMENT-001 ~ 016)"));
children.push(makeTable(["FR ID","Requirement","Priority"],[["FR-PAYMENT-001","Seller Stripe Connect KYC onboarding","HIGH"],["FR-PAYMENT-002","Tạo Stripe PaymentIntent khi checkout (trigger: payment.requested)","HIGH"],["FR-PAYMENT-003","PaymentIntent idempotency: skip nếu đã có PENDING/SUCCESS","HIGH"],["FR-PAYMENT-004","Xử lý Stripe webhook events với signature verification (9 event types)","HIGH"],["FR-PAYMENT-005","Payment success: TRANSACTION→SUCCESS, SELLER_TRANSFER→AWAITING_DELIVERY, publish payment.success","HIGH"],["FR-PAYMENT-006","Payment failure: TRANSACTION→FAILED, publish payment.failed","HIGH"],["FR-PAYMENT-007","Auto-cancel unpaid orders sau 30ph (Axon Deadline + JOB-13 safety net)","HIGH"],["FR-PAYMENT-008","RTS auto-refund: FULL refund không cần admin (trigger: order.returned)","HIGH"],["FR-PAYMENT-009","Cryptographic Stripe webhook signature verification","HIGH"],["FR-PAYMENT-010","Commission calculation: 5% mặc định, net_payout = transfer_amount - commission","HIGH"],["FR-PAYMENT-011","Delayed payout: RETURN_WINDOW → READY_FOR_PAYOUT sau +7d, JOB-23 gọi Stripe Transfer","HIGH"],["FR-PAYMENT-012","Transaction status aggregation (SUCCESS/PARTIALLY_REFUNDED/REFUNDED)","MEDIUM"],["FR-PAYMENT-013","Validate refund eligibility: return window, balance, evidence","HIGH"],["FR-PAYMENT-014","Mandatory evidence image upload cho BUYER_REQUEST refunds (>=1 ảnh)","HIGH"],["FR-PAYMENT-015","Admin approve/reject refund requests","HIGH"],["FR-PAYMENT-016","Execute Stripe refund: pre-payout Refund.create, post-payout Transfer reversal","HIGH"]], [2000, 6580, 1500]));
children.push(sp());

children.push(h2("5.7 flashsale-service (FR-FLASHSALE-001 ~ 012)"));
children.push(makeTable(["FR ID","Requirement","Priority"],[["FR-FLASHSALE-001","Admin tạo session: name, start_time, end_time, discount_percentage (0-100]","HIGH"],["FR-FLASHSALE-002","Auto-calc registration_deadline = start_time - 15 phút","HIGH"],["FR-FLASHSALE-003","Validate end_time > start_time → HTTP 400 INVALID_TIME_RANGE","HIGH"],["FR-FLASHSALE-004","Seller đăng ký product vào UPCOMING session (trước registration_deadline)","HIGH"],["FR-FLASHSALE-005","Auto-approve mọi valid registration (không manual step)","HIGH"],["FR-FLASHSALE-006","Admin cập nhật session (chỉ UPCOMING): name, time, discount → recalc deadline","MEDIUM"],["FR-FLASHSALE-007","Auto-transition status: Redis ZSET trigger → UPCOMING→ACTIVE→ENDED, near-zero latency","CRITICAL"],["FR-FLASHSALE-008","View sessions: public GET (UPCOMING+ACTIVE), admin GET (all+pagination), active GET (Redis cache)","HIGH"],["FR-FLASHSALE-009","Atomic Redis Lua purchase: check stock→decrement→reserve, chống oversell 50k+ req/s","CRITICAL"],["FR-FLASHSALE-010","Dynamic flash price: sku.price * (1 - discount_applied/100), tính real-time, không materialize","HIGH"],["FR-FLASHSALE-011","Customer set reminder: 1 reminder/customer/session, 409 REMINDER_ALREADY_SET nếu trùng","MEDIUM"],["FR-FLASHSALE-012","Publish Kafka events: session_created/started/ended, item_registered, item_purchased","HIGH"]], [2000, 6580, 1500]));
children.push(sp());

children.push(h2("5.8 search-service (FR-SEARCH-001 ~ 005)"));
children.push(makeTable(["FR ID","Requirement","Priority"],[["FR-SEARCH-001","Full-text search với Vietnamese analysis + fuzzy matching, field collapse by product_id","HIGH"],["FR-SEARCH-002","Filters (category, price range, stock, flash) + aggregation facets (color, size, price)","HIGH"],["FR-SEARCH-003","Autocomplete suggestions: min 2 chars, max 10 results, deduplicated","MEDIUM"],["FR-SEARCH-004","Full reindex từ Product Service DB: admin-only, zero-downtime (alias swap), chống concurrent (409)","MEDIUM"],["FR-SEARCH-005","Consume 10 Kafka topics cho near-real-time index updates (product.*, category.*, inventory.*)","HIGH"]], [2000, 6580, 1500]));
children.push(sp());

children.push(h2("5.9 notification-service (FR-NOTIF-001 ~ 004)"));
children.push(makeTable(["FR ID","Requirement","Priority"],[["FR-NOTIF-001","SSE real-time stream: persistent connection, text/event-stream, Last-Event-ID replay","HIGH"],["FR-NOTIF-002","Paginated notification history: filter is_read, sort created_at DESC, max 100/page","HIGH"],["FR-NOTIF-003","Read status: mark single, mark all, unread count — Idempotent, 403 cross-user","HIGH"],["FR-NOTIF-004","Consume 30+ Kafka topics → MongoDB insert <500ms → Redis Pub/Sub notify","HIGH"]], [2000, 6580, 1500]));
children.push(sp());

children.push(h2("5.10 ai-chat-service (FR-AICHAT-001 ~ 006)"));
children.push(makeTable(["FR ID","Requirement","Priority"],[["FR-AICHAT-001","Session management: create, list, close, auto-expire 30ph idle","HIGH"],["FR-AICHAT-002","SSE streaming AI: delta events (token), tool_start/done, products/order cards, confirmation_required, done","HIGH"],["FR-AICHAT-003","Human-in-the-loop: CONFIRMED→execute+publish, REJECTED→skip, EXPIRED (5ph TTL)→400","HIGH"],["FR-AICHAT-004","Cursor-paginated message history: before parameter (sequence_no), limit 20-50, hasMore+nextCursor","HIGH"],["FR-AICHAT-005","Kafka events: message_received, session.created/closed, confirmation.requested/confirmed/rejected/expired","HIGH"],["FR-AICHAT-006","Contextual suggestions: home/product/order/cart context, personalized với JWT, no LLM call","MEDIUM"]], [2000, 6580, 1500]));
children.push(sp());
children.push(divider());

// ─── 6. BUSINESS RULES ──────────────────────────────────────────
children.push(h1("6. Business Rules"));
[{title:"6.1 identity-service",rules:["BR-AUTH-001: JWT RS256 — access 24h + refresh 7d","BR-AUTH-002: Mật khẩu Bcrypt, min 8 ký tự","BR-AUTH-003: Token blacklist Redis khi logout","BR-AUTH-004: 1 user = 1 role (BUYER/SELLER/ADMIN)"]},{title:"6.2 product-service",rules:["BR-PRODUCT-001: Duyệt sản phẩm: draft→pending→admin→approved/rejected (<=3 lần resubmit)","BR-PRODUCT-002: Tự động ẩn sau 30 ngày inactive","BR-PRODUCT-003: Seller chỉ xem/sửa sản phẩm của mình","BR-PRODUCT-004: Ít nhất 1 variant/sản phẩm, stock>=0, price>0","BR-CART-001: 1 customer = 1 cart active"]},{title:"6.3 order-service",rules:["BR-ORDER-001: PENDING_PAYMENT→PAID→SHIPPED→DELIVERED","BR-ORDER-002: Timeout 30ph → auto-cancel","BR-ORDER-003: Return window = delivered_at + 7d","BR-ORDER-004: RTS → auto-refund không cần admin","BR-ORDER-005: 1 parent_order = N sub-orders (theo seller)"]},{title:"6.4 payment-service",rules:["BR-PAYMENT-001: Stripe Connect Destination Charges + Transfer API","BR-PAYMENT-002: Commission 5% (STRIPE_PLATFORM_FEE_PERCENTAGE)","BR-PAYMENT-003: Delayed payout sau delivery + 7d return window","BR-PAYMENT-004: Seller phải có Stripe account (charges_enabled)","BR-PAYMENT-005: PaymentIntent idempotency theo parent_order_id"]},{title:"6.5 refund-service",rules:["BR-REFUND-001: Buyer request trong 7d return window","BR-REFUND-002: Evidence images bắt buộc với BUYER_REQUEST","BR-REFUND-003: Admin review gate trước Stripe execution","BR-REFUND-004: Pre-payout→REFUNDED; Post-payout→Stripe reversal","BR-REFUND-005: RTS auto-refund không cần admin","BR-REFUND-006: group_ref UUID nhóm nhiều refund"]},{title:"6.6 flashsale-service",rules:["BR-FS-001: Redis Lua atomic buy, chống oversell","BR-FS-002: UPCOMING→ACTIVE→ENDED","BR-FS-003: Mỗi product 1 lần/session"]},{title:"6.7 notification-service",rules:["BR-NOTIF-001: SSE real-time push","BR-NOTIF-002: Consumer 22+ Kafka topics"]},{title:"6.8 chat-service",rules:["BR-CHAT-001: AI multi-turn + tool calling","BR-CHAT-002: Human-in-the-loop cho sensitive actions"]}].forEach(sec => { children.push(h2(sec.title)); sec.rules.forEach(r => children.push(bullet(r))); children.push(sp()); });
children.push(divider());

// ─── 7. USE CASES ───────────────────────────────────────────────
children.push(h1("7. Use Cases"));
[{title:"7.1 identity-service",rows:[["UC-IDENTITY-001","Register","Public"],["UC-IDENTITY-002","Login","Public"],["UC-IDENTITY-003","Refresh Token","Public"],["UC-IDENTITY-004","Manage Addresses","Buyer"],["UC-IDENTITY-005","Manage Users","Admin"]]},{title:"7.2 product-service",rows:[["UC-PRODUCT-001","Browse Products","Public"],["UC-PRODUCT-002","Create Product","Seller"],["UC-PRODUCT-003","Admin Review","Admin"],["UC-PRODUCT-004","Manage Cart","Buyer"],["UC-PRODUCT-005","Reserve Stock","System"]]},{title:"7.3 order-service",rows:[["UC-ORDER-001","Checkout","Buyer"],["UC-ORDER-002","View Orders","Buyer"],["UC-ORDER-003","Ship Order","Seller"],["UC-ORDER-004","Confirm Received","Buyer"],["UC-ORDER-005","Cancel Order","Buyer/Seller"],["UC-ORDER-006","Request Refund","Buyer"],["UC-ORDER-007","Full Refund","Buyer"],["UC-ORDER-008","Return To Sender","Seller"]]},{title:"7.4 payment-service",rows:[["UC-PAYMENT-001","Stripe Onboarding","Seller"],["UC-PAYMENT-002","Payment Intent","System"],["UC-PAYMENT-003","Webhook Handler","System"],["UC-PAYMENT-004","Seller Payout","System (Cron)"]]},{title:"7.5 refund-service",rows:[["UC-REFUND-001","Create Refund","System"],["UC-REFUND-002","Approve Refund","Admin"],["UC-REFUND-003","Reject Refund","Admin"],["UC-REFUND-004","Auto RTS Refund","System"],["UC-REFUND-005","Chargeback Handler","System"]]},{title:"7.6 flashsale-service",rows:[["UC-FS-001","Create Session","Admin"],["UC-FS-002","Register Product","Seller"],["UC-FS-003","Buy Item","Buyer"],["UC-FS-004","Set Reminder","Buyer"]]},{title:"7.7 search-service",rows:[["UC-SEARCH-001","Full-text Search","Public"]]},{title:"7.8 notification-service",rows:[["UC-NOTIF-001","Receive Notifications","Buyer/Seller"],["UC-NOTIF-002","Mark as Read","Buyer/Seller"]]},{title:"7.9 chat-service",rows:[["UC-CHAT-001","AI Chat","Buyer"],["UC-CHAT-002","Tool Call","System"],["UC-CHAT-003","Confirm Action","Buyer"]]}].forEach(sec => { children.push(h2(sec.title)); children.push(makeTable(["UC ID","Tên","Actor"], sec.rows, [2500, 5580, 2000])); children.push(sp()); });
children.push(divider());

// ─── 8. STATE MACHINES ──────────────────────────────────────────
children.push(h1("8. State Machines"));
[{title:"8.1 User (identity-service)",rows:[["1","ACTIVE","Admin lock","LOCKED","Thu hồi token, Redis blocklist"],["2","LOCKED","Admin unlock","ACTIVE","Xoá blocklist"]]},{title:"8.2 Product (product-service)",rows:[["1","draft","Seller submit","pending","Gửi admin duyệt"],["2","pending","Admin approve","approved","Ghi reviewed_by, reviewed_at"],["3","pending","Admin reject","rejected","Ghi reject_reason, tăng reject_count"],["4","rejected","Seller resubmit (<=3)","draft",">=3 → không cho resubmit"],["5","approved","Seller publish","active","Hiển thị search + catalog"],["6","active","Seller unpublish","inactive","Ẩn search"],["7","active","stock=0 (all variants)","out_of_stock","Auto"],["8","out_of_stock","Restock","active","Khi có variant còn hàng"],["9","inactive","30 ngày","auto_hidden","Cronjob"]]},{title:"8.3 StockReservation (product-service)",rows:[["1","pending","Checkout confirmed","confirmed",""],["2","pending","Timeout 15ph","released",""]]},{title:"8.4 ParentOrder (order-service)",rows:[["1","PENDING_PAYMENT","payment.success","PAID",""],["2","PENDING_PAYMENT","Timeout 30ph","CANCELLED",""],["3","PENDING_PAYMENT","Buyer huỷ","CANCELLED",""]]},{title:"8.5 Order (order-service)",rows:[["1","PENDING","payment.success","CONFIRMED",""],["2","PENDING","Huỷ","CANCELLED",""],["3","CONFIRMED","Seller ship","SHIPPED",""],["4","CONFIRMED","Huỷ","CANCELLED",""],["5","SHIPPED","Buyer confirm","DELIVERED",""],["6","SHIPPED","Auto-confirm 7d","DELIVERED",""],["7","DELIVERED","Seller RTS","RETURNED",""],["8","RETURNED","Auto-refund OK","(end)",""]]},{title:"8.6 Transaction (payment-service)",rows:[["1","PENDING","pi.succeeded","SUCCESS",""],["2","PENDING","pi.failed","FAILED",""],["3","PENDING","pi.canceled","CANCELLED",""]]},{title:"8.7 SellerTransfer (payment-service)",rows:[["1","PENDING","Payment success","AWAITING_DELIVERY",""],["2","PENDING","No Stripe account","SKIPPED",""],["3","AWAITING_DELIVERY","Order delivered","RETURN_WINDOW",""],["4","RETURN_WINDOW","+7d (JOB-23)","READY_FOR_PAYOUT",""],["5","READY_FOR_PAYOUT","Stripe payout OK","PAID_OUT",""],["6","AWAITING_DELIVERY","Refund (pre-payout)","REFUNDED",""],["7","RETURN_WINDOW","Refund (pre-payout)","REFUNDED",""],["8","READY_FOR_PAYOUT","Refund (pre-payout)","REFUNDED",""],["9","PAID_OUT","Refund (post-payout)","REVERSED/PARTIALLY_REVERSED",""],["10","PAID_OUT","Payout failed","FAILED",""]]},{title:"8.8 Refund (refund-service)",rows:[["1","(new)","refund.requested","PENDING",""],["2","(new)","order.returned (RTS)","SUCCESS",""],["3","(new)","refund.stripe_auto","SUCCESS",""],["4","PENDING","Admin approve + Stripe OK","SUCCESS",""],["5","PENDING","Stripe error","FAILED",""],["6","FAILED","Admin retry + Stripe OK","SUCCESS",""],["7","PENDING","Admin reject","REJECTED",""]]},{title:"8.9 FsSession (flashsale-service)",rows:[["1","UPCOMING","start_time (Redis ZSET)","ACTIVE",""],["2","ACTIVE","end_time (Redis ZSET)","ENDED",""]]}].forEach(sec => { children.push(h2(sec.title)); children.push(makeTable(["#","Current State","Trigger","Next State","Ghi chú"], sec.rows, [400, 1800, 2500, 1800, 3580])); children.push(sp()); });
children.push(divider());

// ─── 9. KAFKA EVENTS ────────────────────────────────────────────
children.push(h1("9. Kafka Events"));
children.push(para("Tổng cộng 58 topics (44 event + 14 request-reply)"));
children.push(sp());
children.push(h2("9.1 Producers & Consumers"));
children.push(makeTable(["Service","Produces","Consumes"],[["identity","—","order.delivered, order.cancelled, refund.*"],["product","product.*, category.*, inventory.*, stock.reservation.*","order.created, order.cancelled, flash_sale.*"],["order","order.*, payment_timeout, seller.order_cancelled","payment.*, refund.*, stock.reservation.expired"],["payment","payment.*, stripe.*, seller.transfer.*, refund.stripe_auto","payment.requested, order.delivered, order.cancelled"],["refund","refund.created, refund.admin_approved, refund.rejected, refund.rts_completed","refund.requested, refund.full_requested, order.returned, order.refunds.request"],["flashsale","flash_sale.*","—"],["search","—","product.*, category.*, inventory.*"],["notification","—","22+ topics"],["chat","ai_chat.*","—"]], [1500, 4290, 4290]));
children.push(sp());
children.push(h2("9.2 Key Flows"));
["Checkout: order.created → payment.requested → Stripe PI → payment.success","Refund: refund.requested → admin review → refund.admin_approved → Stripe","RTS: order.returned → auto-refund → refund.rts_completed","Flash Sale: Redis Lua atomic → flash_sale.item_purchased","Chargeback: Stripe webhook → refund.stripe_auto"].forEach(f => children.push(bullet(f)));
children.push(sp());
children.push(h2("9.3 Request-Reply (7 pairs)"));
children.push(para("Cart↔Product · Order↔Product (stock, cart_items) · Order↔Payment (status, refunds) · Order↔Identity (address)"));
children.push(sp());
children.push(divider());

// ─── 10. INFRASTRUCTURE ─────────────────────────────────────────
children.push(h1("10. Infrastructure & Operations"));
children.push(h2("10.1 Cronjobs"));
children.push(makeTable(["ID","Tên","Status","Service"],[["JOB-23","PayoutScheduler","Implemented","payment"],["JOB-13","Auto-cancel unpaid","Post-MVP","order"]], [1200, 3000, 2000, 3880]));
children.push(sp());
children.push(h2("10.2 Monitoring"));
["Eureka: :8761","Axon GUI: :8024","MinIO: :9001"].forEach(m => children.push(bullet(m)));
children.push(sp());
children.push(h2("10.3 Quick Start"));
children.push(new Paragraph({spacing:{before:60,after:60},children:[new TextRun({text:"# Dev",font:"Courier New",size:18,color:"333333"})]}));
children.push(new Paragraph({spacing:{before:0,after:60},children:[new TextRun({text:"docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d",font:"Courier New",size:18,color:"C0392B"})]}));
children.push(new Paragraph({spacing:{before:60,after:60},children:[new TextRun({text:"# Prod",font:"Courier New",size:18,color:"333333"})]}));
children.push(new Paragraph({spacing:{before:0,after:60},children:[new TextRun({text:"docker compose -f docker-compose.yml -f docker-compose.prod-pulled.yml up -d",font:"Courier New",size:18,color:"C0392B"})]}));
children.push(sp());
children.push(h2("10.4 CI/CD"));
children.push(makeTable(["Pipeline","Trigger","Actions"],[["CI","Push non-main, PR→develop","Validate → Maven package → Dockerfile.dev → GHCR"],["CD","Merge→main, push→main","Maven package → Dockerfile.prod → GHCR → Deploy server"]], [1500, 3000, 5580]));
children.push(sp());
children.push(para("Images: ghcr.io/{owner}/flashsale-{service}:{dev|prod}-latest"));
children.push(sp());
children.push(h2("10.5 Key Environment Variables"));
["JWT_SECRET","POSTGRES_USER / POSTGRES_PASSWORD","MONGO_INITDB_ROOT_USERNAME / MONGO_INITDB_ROOT_PASSWORD","REDIS_PASSWORD","MINIO_ACCESS_KEY / MINIO_SECRET_KEY","STRIPE_SECRET_KEY / STRIPE_WEBHOOK_SECRET","STRIPE_PLATFORM_FEE_PERCENTAGE","MAIL_PASSWORD","JVM_OPTS_*"].forEach(v => children.push(bullet(v)));

// ─── BUILD ───────────────────────────────────────────────────────
const doc = new Document({
  numbering: {config:[{reference:"bullets",levels:[{level:0,format:LevelFormat.BULLET,text:"•",alignment:AlignmentType.LEFT,style:{paragraph:{indent:{left:720,hanging:360}}}}]}]},
  styles: {
    default:{document:{run:{font:"Arial",size:22}}},
    paragraphStyles:[
      {id:"Heading1",name:"Heading 1",basedOn:"Normal",next:"Normal",quickFormat:true,run:{size:36,bold:true,font:"Arial",color:HEADER_COLOR},paragraph:{spacing:{before:360,after:180},outlineLevel:0}},
      {id:"Heading2",name:"Heading 2",basedOn:"Normal",next:"Normal",quickFormat:true,run:{size:28,bold:true,font:"Arial",color:"2E5FA3"},paragraph:{spacing:{before:240,after:120},outlineLevel:1}},
      {id:"Heading3",name:"Heading 3",basedOn:"Normal",next:"Normal",quickFormat:true,run:{size:24,bold:true,font:"Arial",color:"3A6CC4"},paragraph:{spacing:{before:160,after:80},outlineLevel:2}}
    ]
  },
  sections:[{properties:{page:{size:{width:PAGE_WIDTH,height:15840},margin:{top:MARGIN,right:MARGIN,bottom:MARGIN,left:MARGIN}}},children}]
});

const outPath = '/mnt/d/dev/stealing-from-paradise/documents/PROJECT_REPORT.docx';
Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(outPath, buf);
  console.log('Done! Written to', outPath);
}).catch(e => { console.error(e); process.exit(1); });
