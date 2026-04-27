package com.flashsale.productdomain.config;

public final class RedisKeys {
    private RedisKeys() {}

    public static String stockKey(java.util.UUID skuId) {
        return "stock:" + skuId;
    }

    public static String checkoutPreviewKey(Long customerId) {
        return "checkout_preview:" + customerId;
    }
}
