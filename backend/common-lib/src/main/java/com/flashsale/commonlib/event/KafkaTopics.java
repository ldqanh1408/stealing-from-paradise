package com.flashsale.commonlib.event;

public final class KafkaTopics {

    private KafkaTopics() {}   // utility class

    // ──────────────────────────────────────────────
    // Account & Trust Score  (Producer: Identity / Worker)
    // ──────────────────────────────────────────────
    public static final String ACCOUNT_AUTO_LOCKED      = "account.auto_locked";
    public static final String ACCOUNT_LOCKED           = "account.locked";
    public static final String ACCOUNT_UNLOCKED         = "account.unlocked";
    public static final String TRUST_SCORE_WARNING      = "trust_score.warning";
    public static final String SELLER_POSTING_SUSPENDED = "seller.posting_suspended";
    public static final String SELLER_POSTING_RESUMED   = "seller.posting_resumed";

    // ──────────────────────────────────────────────
    // Product  (Producer: Product Service / Identity Service)
    // ──────────────────────────────────────────────
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

    // ──────────────────────────────────────────────
    // Payment  (Producer: Payment Service)
    // ──────────────────────────────────────────────
    public static final String PAYMENT_SUCCESS          = "payment.success";
    public static final String PAYMENT_FAILED           = "payment.failed";

    // ──────────────────────────────────────────────
    // Refund  (Producer: Payment Service)
    // ──────────────────────────────────────────────
    public static final String REFUND_REQUESTED         = "refund.requested";
    public static final String REFUND_ADMIN_APPROVED    = "refund.admin_approved";
    public static final String REFUND_REJECTED          = "refund.rejected";
    public static final String REFUND_RTS_COMPLETED     = "refund.rts_completed";

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
    // ──────────────────────────────────────────────
    public static final String CART_PRODUCT_INFO_REQUEST     = "cart.product_info.request";
    public static final String CART_PRODUCT_INFO_RESPONSE    = "cart.product_info.response";
    public static final String ORDER_STOCK_CHECK_REQUEST     = "order.stock_check.request";
    public static final String ORDER_STOCK_CHECK_RESPONSE    = "order.stock_check.response";
    public static final String ORDER_PAYMENT_STATUS_REQUEST  = "order.payment_status.request";
    public static final String ORDER_PAYMENT_STATUS_RESPONSE = "order.payment_status.response";

}

