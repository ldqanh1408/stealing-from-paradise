package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The full order/payment lifecycle across product-service → Kafka → order-service
 * (Axon sagas) → payment-service (Stripe) and back:
 *
 *   checkout  → sub-orders PENDING, Stripe PaymentIntent + transaction PENDING
 *   pay OK    → payment.success → ParentOrderPaymentSaga → sub-orders PAID
 *   pay fail  → payment.failed  → saga compensates → sub-orders CANCELLED
 *   cancel    → order.cancelled → payment-service cancels PI + transaction
 *
 * Stripe webhook delivery is the only simulated step (validly-signed payloads);
 * everything else, including signature verification, runs production code.
 */
@DisplayName("E2E-A04: order & payment lifecycle")
class A04OrderPaymentE2eTest extends E2eSupport {

    @Test
    @DisplayName("checkout → payment succeeded → all sub-orders PAID")
    void paymentSucceededPath() {
        String buyer = login(BUYER);
        long parentOrderId = checkout(buyer);

        awaitAllSubOrders(buyer, parentOrderId, "PENDING");
        awaitTransactionStatus(buyer, parentOrderId, "PENDING");

        sendStripeWebhook("payment_intent.succeeded", parentOrderId);

        awaitAllSubOrders(buyer, parentOrderId, "PAID");
        awaitTransactionStatus(buyer, parentOrderId, "SUCCESS");
    }

    @Test
    @DisplayName("checkout → payment failed → all sub-orders CANCELLED")
    void paymentFailedPath() {
        String buyer = login(BUYER);
        long parentOrderId = checkout(buyer);

        awaitAllSubOrders(buyer, parentOrderId, "PENDING");
        awaitTransactionStatus(buyer, parentOrderId, "PENDING");

        sendStripeWebhook("payment_intent.payment_failed", parentOrderId);

        awaitAllSubOrders(buyer, parentOrderId, "CANCELLED");
        awaitTransactionStatus(buyer, parentOrderId, "FAILED");
    }

    @Test
    @DisplayName("buyer cancels PENDING order → order CANCELLED and transaction cleaned up")
    void buyerCancelPath() {
        String buyer = login(BUYER);
        long parentOrderId = checkout(buyer);

        awaitAllSubOrders(buyer, parentOrderId, "PENDING");
        awaitTransactionStatus(buyer, parentOrderId, "PENDING");

        List<JsonNode> subs = subOrders(parentOrderDetail(buyer, parentOrderId));
        assertFalse(subs.isEmpty(), "parent order should expose sub-orders");
        for (JsonNode sub : subs) {
            HttpResponse<String> resp = post("/api/v1/orders/" + subOrderId(sub) + "/cancel", buyer,
                    Map.of("reason", "E2E test cancellation"));
            assertEquals(200, resp.statusCode(), "cancel failed: " + resp.body());
        }

        awaitAllSubOrders(buyer, parentOrderId, "CANCELLED");
        awaitTransactionStatus(buyer, parentOrderId, "CANCELLED");
    }
}
