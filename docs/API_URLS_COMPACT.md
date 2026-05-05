# API Endpoints — Toàn bộ Hệ thống

> Base path: `/api/v1` → Gateway stripPrefix(1) → `/{service}:{port}`

---

## Identity Service (`identity-service:8081`)

### Public
| Method | Path | Ghi chú |
|--------|------|---------|
| POST | /auth/register | Đăng ký |
| POST | /auth/login | Đăng nhập → JWT |
| POST | /auth/refresh | Refresh token |

### JWT Required
| Method | Path | Ghi chú |
|--------|------|---------|
| POST | /auth/logout | Thu hồi token |
| GET | /users/me | Thông tin cá nhân |
| PUT | /users/me | Cập nhật profile |
| GET | /users/me/avatar/presigned-url | Upload avatar |
| GET | /users/me/addresses | DS địa chỉ |
| POST | /users/me/addresses | Thêm địa chỉ |
| PUT | /users/me/addresses/{addressId} | Sửa địa chỉ |
| DELETE | /users/me/addresses/{addressId} | Xóa địa chỉ |

### Admin (JWT + ADMIN)
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /admin/users | DS người dùng |

---

## Product Service (`product-service:8090`)

### Public
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /products | DS sản phẩm |
| GET | /products/{productId} | Chi tiết sản phẩm |
| GET | /categories | DS danh mục |

### JWT Required
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /cart | Giỏ hàng |
| POST | /cart/items | Thêm vào giỏ |
| PUT | /cart/items/{itemId} | Sửa số lượng |
| DELETE | /cart/items/{itemId} | Xóa item |
| DELETE | /cart | Xóa giỏ hàng |
| GET | /inventory/{skuCode} | Tồn kho |

### Seller (JWT + SELLER)
| Method | Path | Ghi chú |
|--------|------|---------|
| POST | /products | Tạo sản phẩm |
| PUT | /products/{productId} | Sửa sản phẩm |
| DELETE | /products/{productId} | Xóa mềm |
| GET | /products/{productId}/presigned-url | Upload ảnh SP |
| GET | /sellers/me/products | DS sản phẩm của tôi |
| GET | /seller/products/{productId}/variants | DS variants |
| POST | /seller/products/{productId}/variants | Thêm variant |
| PUT | /seller/variants/{variantId} | Sửa variant |
| DELETE | /seller/variants/{variantId} | Xóa variant |
| POST | /seller/products/{productId}/submit | Gửi duyệt |
| POST | /seller/products/{productId}/publish | Mở bán |
| POST | /seller/products/{productId}/unpublish | Tạm ẩn |
| PUT | /inventory/{skuCode}/restock | Nhập hàng |
| POST | /seller/inventory/adjust | Điều chỉnh tồn |
| GET | /seller/inventory/{skuCode}/logs | Log tồn kho |

### Admin (JWT + ADMIN)
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /admin/products/pending | SP chờ duyệt |
| POST | /admin/products/{productId}/approve | Duyệt SP |
| POST | /admin/products/{productId}/reject | Từ chối SP |
| POST | /admin/categories | Tạo danh mục |
| PUT | /admin/categories/{categoryId} | Sửa danh mục |
| DELETE | /admin/categories/{categoryId} | Xóa danh mục |

---

## Order Service (`order-service:8083`)

### Public
| Method | Path | Ghi chú |
|--------|------|---------|

### Buyer (JWT + BUYER)
| Method | Path | Ghi chú |
|--------|------|---------|
| POST | /orders/checkout | Tạo đơn (multi-vendor) |
| GET | /orders | DS đơn hàng |
| GET | /orders/{orderId} | Chi tiết đơn |
| GET | /orders/parent/{parentOrderId} | Chi tiết đơn cha |
| POST | /orders/{orderId}/cancel | Hủy đơn |
| POST | /orders/{orderId}/confirm-received | Xác nhận nhận hàng |
| POST | /orders/{orderId}/return-to-sender | Trả hàng |
| POST | /orders/{orderId}/refunds | Hoàn tiền 1 phần |
| POST | /orders/parent/{parentOrderId}/refund | Full refund |
| POST | /orders/parent/{parentOrderId}/refunds/partial | Hoàn multi-seller |
| GET | /orders/parent/{parentOrderId}/refund | Trạng thái full refund |
| GET | /orders/refunds | DS yêu cầu hoàn tiền |
| GET | /orders/{orderId}/refunds | Lịch sử hoàn của đơn |
| GET | /orders/{orderId}/refunds/{refundId} | Chi tiết hoàn tiền |
| GET | /orders/{orderId}/refunds/presigned-url | Upload bằng chứng |

### Seller (JWT + SELLER)
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /orders/{orderId} | Chi tiết đơn |
| GET | /sellers/me/orders | DS đơn của tôi |
| PUT | /orders/{orderId}/tracking | Cập nhật vận đơn |
| GET | /orders/{orderId}/refunds | Lịch sử hoàn |

### Admin (JWT + ADMIN)
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /orders/parent/{parentOrderId} | Chi tiết đơn cha |
| GET | /orders/parent/{parentOrderId}/refund | Trạng thái refund |
| GET | /orders/{orderId}/refunds | Lịch sử hoàn |

---

## Payment Service (`payment-service:8082`)

### Public
| Method | Path | Ghi chú |
|--------|------|---------|
| POST | /stripe/webhooks | Stripe webhook (Stripe sig) |

### Seller (JWT + SELLER)
| Method | Path | Ghi chú |
|--------|------|---------|
| POST | /stripe/onboarding/start | Bắt đầu KYC Stripe |
| GET | /stripe/onboarding/status | Trạng thái KYC |
| POST | /stripe/onboarding/refresh-link | Link mới khi hết hạn |
| GET | /seller/payments/transfers | Lịch sử chuyển tiền |
| GET | /seller/payments/balance | Số dư khả dụng |

### Buyer/Admin (JWT)
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /payments/parent-order/{parentOrderId} | Trạng thái thanh toán |
| GET | /payments/by-intent/{stripePaymentIntentId} | Tra cứu theo Payment Intent |

### Admin (JWT + ADMIN)
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /admin/refunds | DS hoàn tiền |
| POST | /admin/refunds/{refundId}/approve | Duyệt hoàn tiền |
| POST | /admin/refunds/{refundId}/reject | Từ chối hoàn tiền |

---

## Flash Sale Service (`flashsale-service:8085`)

### Public
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /flash-sale/sessions | DS session |
| GET | /flash-sale/sessions/{sessionId} | Chi tiết session + items |

### Buyer (JWT + BUYER)
| Method | Path | Ghi chú |
|--------|------|---------|
| POST | /flash-sale/sessions/{sessionId}/buy | Mua flash sale |
| POST | /flash-sale/sessions/{sessionId}/reminders | Đăng ký nhắc nhở |

### JWT Required
| Method | Path | Ghi chú |
|--------|------|---------|
| DELETE | /flash-sale/sessions/{sessionId}/reminders | Hủy nhắc nhở |

### Seller (JWT + SELLER)
| Method | Path | Ghi chú |
|--------|------|---------|
| POST | /flash-sale/sessions/{sessionId}/items | Đăng ký SP |

### Admin (JWT + ADMIN)
| Method | Path | Ghi chú |
|--------|------|---------|
| POST | /flash-sale/sessions | Tạo session |
| GET | /admin/flash-sale/sessions | DS tất cả session |
| PUT | /admin/flash-sale/sessions/{sessionId} | Sửa session |
| DELETE | /admin/flash-sale/sessions/{sessionId} | Xóa session |
| POST | /flash-sale/sessions/{sessionId}/items/{itemId}/approve | Duyệt item |
| POST | /admin/flash-sale/items/{itemId}/reject | Từ chối item |

---

## Search Service (`search-service:8091`)

### Public
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /search/products | Tìm kiếm full-text |
| GET | /search/products/suggest | Autocomplete |

---

## Notification Service (`notification-service:8092`)

### JWT Required
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /notifications/stream | SSE real-time stream |
| GET | /notifications | DS thông báo |
| PATCH | /notifications/{notifId}/read | Đánh dấu đã đọc |
| PATCH | /notifications/read-all | Đánh dấu tất cả đã đọc |
| GET | /notifications/unread-count | Đếm chưa đọc |

---

## Worker Service (`worker-service:8086`)

### Admin (JWT + ADMIN)
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /admin/failed-events | DS event thất bại |
| POST | /admin/failed-events/{eventId}/retry | Retry event |
| POST | /admin/failed-events/{eventId}/resolve | Đánh dấu đã xử lý |

---

## AI Chat Service (`ai-chat-service:8093`)

> Base path: `/api/ai` → Gateway routes to `ai-chat-service`

### Public (Optional JWT)
| Method | Path | Ghi chú |
|--------|------|---------|
| GET | /api/ai/suggest | Gợi ý câu hỏi nhanh |

### JWT Required
| Method | Path | Ghi chú |
|--------|------|---------|
| POST | /api/ai/chat | Chat streaming (SSE) |
| GET | /api/ai/chat/history | Lịch sử hội thoại |
| POST | /api/ai/sessions | Tạo session mới |
| DELETE | /api/ai/sessions/{sessionId} | Đóng session |
| POST | /api/ai/confirm | Xác nhận/từ chối action Mức 3 |

---

## Tổng hợp

| Service | Public | JWT | Seller | Buyer | Admin | Tổng |
|---------|--------|-----|--------|-------|-------|------|
| Identity | 3 | 12 | - | - | 3 | 18 |
| Product | 3 | 5 | 16 | - | 6 | 30 |
| Order | 0 | - | 4 | 15 | 3 | 22 |
| Payment | 1 | 2 | 5 | - | 3 | 11 |
| Flash Sale | 2 | 1 | 1 | 2 | 6 | 12 |
| Search | 2 | - | - | - | - | 2 |
| Notification | - | 5 | - | - | - | 5 |
| Worker | - | - | - | - | 3 | 3 |
| AI Chat | 1 | 5 | - | - | - | 6 |
| **Tổng** | **12** | **29** | **26** | **17** | **27** | **111** |

> Ghi chú: Một số endpoint được nhiều role truy cập (vd: GET /orders/{orderId} cho cả BUYER lẫn SELLER) được tính vào mỗi role tương ứng.
