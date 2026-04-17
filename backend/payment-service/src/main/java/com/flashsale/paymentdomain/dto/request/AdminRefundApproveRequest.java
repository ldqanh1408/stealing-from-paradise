package com.flashsale.paymentdomain.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class AdminRefundApproveRequest {

    @NotBlank(message = "admin_note is required")
    @Size(min = 1, max = 1000, message = "admin_note must be 1–1000 characters")
    @JsonProperty("admin_note")
    private String adminNote;

    @JsonProperty("adjust_amount")
    private BigDecimal adjustAmount;

    @JsonProperty("caused_by")
    private String causedBy;   // SELLER | BUYER

    @Pattern(regexp = "^[A-Z]{2}[0-9]{9}$", message = "tracking_number must match [A-Z]{2}[0-9]{9}")
    @JsonProperty("tracking_number")
    private String trackingNumber;
}
