# 🛡️ Admin APIs (Merged)

> **Admin Service đã được gộp vào Identity Service.**
>
> Toàn bộ admin endpoints được documented tại:
> 🔗 [identity-service/02_API_identity_service.md](../identity-service/02_API_identity_service.md) → **Admin Management** section
>
> Các admin endpoints được route từ API Gateway dưới prefix `/admin/**` và được xử lý bởi
> **Identity Service** (user management, product moderation, flash sale, refund, failed events).
> Tất cả yêu cầu JWT + ADMIN role.

---

## Endpoints Quick Reference

| Endpoint | Method | Mô tả |
|----------|--------|-------|
| /admin/products/pending | GET | Sản phẩm chờ duyệt |
| /admin/products/{id}/approve | POST | Duyệt sản phẩm |
| /admin/products/{id}/reject | POST | Từ chối sản phẩm |
| /admin/categories | POST | Tạo danh mục |
| /admin/categories/{id} | PUT | Cập nhật danh mục |
| /admin/categories/{id} | DELETE | Xóa danh mục |
| /admin/users | GET | Danh sách users |
| /admin/users/{id}/lock | POST | Khóa tài khoản |
| /admin/users/{id}/unlock | POST | Mở khóa tài khoản |
| /admin/users/{id}/unlock-product-posting | POST | Gỡ tạm dừng đăng bài |
| /admin/flash-sale/sessions | GET | Danh sách sessions |
| /admin/flash-sale/sessions/{id} | PUT | Cập nhật session |
| /admin/flash-sale/sessions/{id} | DELETE | Xóa session |
| /flash-sale/sessions | POST | Tạo session mới |
| /flash-sale/sessions/{sid}/items/{iid}/approve | POST | Duyệt FS item |
| /admin/flash-sale/items/{id}/reject | POST | Từ chối FS item |
| /admin/refunds | GET | Danh sách refunds |
| /admin/refunds/{id}/approve | POST | Duyệt hoàn tiền |
| /admin/refunds/{id}/reject | POST | Từ chối hoàn tiền |
| /admin/failed-events | GET | Danh sách events lỗi |
| /admin/failed-events/{id}/retry | POST | Retry event |
| /admin/failed-events/{id}/resolve | POST | Mark resolved |

---

> **Phiên bản:** v5.5 — Đã gộp vào Identity Service
