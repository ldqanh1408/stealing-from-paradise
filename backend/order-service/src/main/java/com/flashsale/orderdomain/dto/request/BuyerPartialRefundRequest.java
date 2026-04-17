package com.flashsale.orderdomain.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class BuyerPartialRefundRequest {

    @NotBlank(message = "reason is required")
    private String reason;

    @NotEmpty(message = "items không được để trống")
    @Valid
    private List<BuyerPartialRefundItem> items;

    @JsonProperty("evidence_images")
    private List<String> evidenceImages;
}
