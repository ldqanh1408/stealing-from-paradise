package com.flashsale.commonlib.event;

public final class KafkaTopics {

    private KafkaTopics() {}   // utility class

    // ──────────────────────────────────────────────
    // Account & Trust Score  (Producer: Identity / Worker)
    // ──────────────────────────────────────────────
    public static final String ACCOUNT_AUTO_LOCKED      = "account.auto_locked";
    public static final String ACCOUNT_LOCKED           = "account.locked";
    public static final String ACCOUNT_UNLOCKED         = "account.unlocked";
    public static final String SELLER_POSTING_SUSPENDED = "seller.posting_suspended";
    public static final String SELLER_POSTING_RESUMED   = "seller.posting_resumed";

    // ──────────────────────────────────────────────
    // Product  (Producer: Product Service / Identity Service)
    // ──────────────────────────────────────────────
    public static final String PRODUCT_CREATED          = "product.created";
    public static final String PRODUCT_PENDING_REVIEW   = "product.pending_review";
    public static final String PRODUCT_APPROVED         = "product.approved";
    public static final String PRODUCT_REJECTED         = "product.rejected";
    public static final String PRODUCT_UPDATED          = "product.updated";
    public static final String PRODUCT_DELETED          = "product.deleted";
    public static final String PRODUCT_AUTO_HIDDEN      = "product.auto_hidden";
    public static final String INVENTORY_ADJUSTED       = "inventory.adjusted";

    // ──────────────────────────────────────────────
    // Order  (Producer: Order Service / Worker)
    // ──────────────────────────────────────────────
    public static final String ORDER_CREATED            = "order.created";
    public static final String ORDER_SHIPPED            = "order.shipped";
    public static final String ORDER_DELIVERED          = "order.delivered";
    public static final String ORDER_RETURNED_RTS       = "order.returned";
    public static final String ORDER_CANCELLED          = "order.cancelled";
    public static final String ORDER_AUTO_CANCELLED     = "order.auto_cancelled";
    public static final String ORDER_CHECKOUT_COMPLETED = "order.checkout_completed"; // → Cart Service: xóa item đã mua
    public static final String SELLER_ORDER_CANCELLED   = "seller.order_cancelled";  // → Identity: Seller hủy đơn

    // ──────────────────────────────────────────────
    // Payment  (Producer: Payment Service)
    // ──────────────────────────────────────────────
    public static final String PAYMENT_REQUESTED        = "payment.requested";
    public static final String PAYMENT_SUCCESS          = "payment.success";
    public static final String PAYMENT_FAILED           = "payment.failed";
    public static final String STRIPE_ACCOUNT_SUSPENDED  = "stripe.account_suspended";
    public static final String STRIPE_DISPUTE_CREATED    = "stripe.dispute.created";
    public static final String STRIPE_DISPUTE_CLOSED     = "stripe.dispute.closed";
    public static final String STRIPE_TRANSFER_REVERSED  = "stripe.transfer.reversed";
    public static final String STRIPE_PAYOUT_FAILED      = "stripe.payout.failed";
    public static final String SELLER_STRIPE_REQUIREMENT = "seller.stripe_requirement"; // → notification-service: seller cần hoàn tất yêu cầu Stripe

    // ──────────────────────────────────────────────
    // Refund  (Producer: Payment Service)
    // ──────────────────────────────────────────────
    public static final String REFUND_REQUESTED         = "refund.requested";
    public static final String REFUND_FULL_REQUESTED    = "refund.full_requested";
    public static final String REFUND_CREATED           = "refund.created";          // payment-service → notification-service (refund record created)
    public static final String REFUND_ADMIN_APPROVED    = "refund.admin_approved";
    public static final String REFUND_REJECTED          = "refund.rejected";
    public static final String REFUND_RTS_COMPLETED     = "refund.rts_completed";
    public static final String REFUND_STRIPE_AUTO       = "refund.stripe_auto";

    // ──────────────────────────────────────────────
    // Flash Sale  (Producer: Flash Sale Service / Worker)
    // ──────────────────────────────────────────────
    public static final String FLASH_SALE_SESSION_STARTED = "flash_sale.session_started";
    public static final String FLASH_SALE_SESSION_ENDED   = "flash_sale.session_ended";
    public static final String FLASH_SALE_ITEM_APPROVED   = "flash_sale.item_approved";
    public static final String FLASH_SALE_ITEM_REJECTED   = "flash_sale.item_rejected";
    public static final String FLASH_SALE_ITEM_SOLD       = "flash_sale.item_sold";
    public static final String FLASH_SALE_REMINDER        = "flash_sale.reminder";

    // ──────────────────────────────────────────────
    // Request-Reply (MVP — thay thế gRPC tạm thời)
    // Cart↔Product · Order↔Product · Order↔Payment
    // Order↔Cart · Order↔Identity
    // ──────────────────────────────────────────────
    public static final String CART_PRODUCT_INFO_REQUEST     = "cart.product_info.request";
    public static final String CART_PRODUCT_INFO_RESPONSE    = "cart.product_info.response";
    public static final String ORDER_STOCK_CHECK_REQUEST     = "order.stock_check.request";
    public static final String ORDER_STOCK_CHECK_RESPONSE    = "order.stock_check.response";
    public static final String ORDER_PAYMENT_STATUS_REQUEST  = "order.payment_status.request";
    public static final String ORDER_PAYMENT_STATUS_RESPONSE = "order.payment_status.response";
    public static final String ORDER_CART_ITEMS_REQUEST      = "order.cart_items.request";
    public static final String ORDER_CART_ITEMS_RESPONSE     = "order.cart_items.response";
    public static final String ORDER_ADDRESS_REQUEST         = "order.address.request";
    public static final String ORDER_ADDRESS_RESPONSE        = "order.address.response";
    public static final String ORDER_REFUNDS_REQUEST         = "order.refunds.request";
    public static final String ORDER_REFUNDS_RESPONSE        = "order.refunds.response";
    public static final String ORDER_REFUND_PRESIGNED_URL_REQUEST  = "order.refund_presigned_url.request";
    public static final String ORDER_REFUND_PRESIGNED_URL_RESPONSE = "order.refund_presigned_url.response";

}
