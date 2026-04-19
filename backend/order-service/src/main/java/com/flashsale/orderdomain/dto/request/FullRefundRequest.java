package com.flashsale.orderdomain.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class FullRefundRequest {

    @NotBlank(message = "reason is required")
    private String reason;

    @JsonProperty("evidence_images")
    private List<String> evidenceImages;
}
