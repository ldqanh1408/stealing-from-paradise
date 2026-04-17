package com.flashsale.paymentdomain.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminRefundRejectRequest {

    @NotBlank(message = "reject_reason is required")
    @JsonProperty("reject_reason")
    private String rejectReason;

    @JsonProperty("fraud_evidence")
    private Boolean fraudEvidence = false;
}
