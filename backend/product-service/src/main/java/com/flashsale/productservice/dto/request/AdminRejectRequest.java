package com.flashsale.productservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminRejectRequest {

    @NotBlank
    private String reason;

    private String note;
}
