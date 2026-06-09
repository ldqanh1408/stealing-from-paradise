package com.flashsale.refundservice.support;

import com.flashsale.refundservice.domain.model.Refund;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

public class RefundMapper {
    public static Map<String, Object> toRefundMap(Refund r) {
        Map<String, Object> m = new HashMap<>();
        m.put("refund_id",          r.getId());
        m.put("refund_code",        RefundCodes.buildRefundCode(r));
        m.put("order_id",           r.getOrderId());
        m.put("group_ref",          r.getGroupRef());
        m.put("type",               r.getType());
        m.put("status",             r.getStatus());
        m.put("amount",             r.getAmount());
        m.put("adjust_amount",      null);
        m.put("reason",             r.getReason());
        m.put("refund_reason_type", r.getRefundReasonType());
        m.put("initiated_by",       r.getInitiatedBy());
        m.put("admin_note",         r.getAdminNote());
        m.put("reject_reason",      r.getRejectReason());
        m.put("reviewed_by",        r.getReviewedBy());
        m.put("reviewed_at",        r.getReviewedAt() != null ? r.getReviewedAt().toInstant(ZoneOffset.UTC).toString() : null);
        m.put("refund_ref",         r.getRefundRef());
        m.put("created_at",         r.getCreatedAt().toInstant(ZoneOffset.UTC).toString());
        return m;
    }
}
