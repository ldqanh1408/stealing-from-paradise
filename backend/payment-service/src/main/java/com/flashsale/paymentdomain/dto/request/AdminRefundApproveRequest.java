package com.flashsale.paymentdomain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class AdminRefundApproveRequest {

    @NotBlank(message = "adminNote is required")
    @Size(min = 1, max = 1000, message = "adminNote must be 1–1000 characters")
    private String adminNote;

    private BigDecimal adjustAmount;

    private String causedBy;   // SELLER | BUYER

    @Pattern(regexp = "^[A-Z]{2}[0-9]{9}$", message = "trackingNumber must match [A-Z]{2}[0-9]{9}")
    private String trackingNumber;
}
