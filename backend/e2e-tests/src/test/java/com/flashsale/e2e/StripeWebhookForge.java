package com.flashsale.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stripe.Stripe;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds Stripe webhook events with a valid HMAC signature so the E2E suite can
 * drive payment outcomes through payment-service's real verification and handler
 * code without depending on Stripe's webhook delivery (the only simulated part
 * is the HTTP delivery itself — signature checking, event parsing and all
 * downstream Kafka/saga processing run for real).
 *
 * The event's api_version is taken from the same stripe-java version that
 * payment-service pins (26.1.0), otherwise EventDataObjectDeserializer refuses
 * to deserialize the payload.
 */
final class StripeWebhookForge {

    private static final ObjectMapper JSON = new ObjectMapper();

    private StripeWebhookForge() {}

    /** A payment_intent.* event whose metadata routes to the given parent order. */
    static String paymentIntentEvent(String eventType, long parentOrderId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        ObjectNode pi = JSON.createObjectNode();
        pi.put("id", "pi_e2e_" + suffix);
        pi.put("object", "payment_intent");
        pi.put("amount", 100000L);
        pi.put("currency", "vnd");
        pi.put("status", "payment_intent.succeeded".equals(eventType) ? "succeeded" : "requires_payment_method");
        pi.putObject("metadata").put("parent_order_id", String.valueOf(parentOrderId));

        ObjectNode event = JSON.createObjectNode();
        event.put("id", "evt_e2e_" + suffix);
        event.put("object", "event");
        event.put("api_version", Stripe.API_VERSION);
        event.put("created", Instant.now().getEpochSecond());
        event.put("livemode", false);
        event.put("pending_webhooks", 1);
        event.put("type", eventType);
        event.putObject("data").set("object", pi);

        return event.toString();
    }

    /** Stripe-Signature header: t=&lt;ts&gt;,v1=HMAC_SHA256(secret, "&lt;ts&gt;.&lt;payload&gt;"). */
    static String signatureHeader(String payload, String webhookSecret) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception e) {
            throw new AssertionError("Could not sign webhook payload", e);
        }
    }
}
