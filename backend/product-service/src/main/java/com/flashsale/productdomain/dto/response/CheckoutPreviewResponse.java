package com.flashsale.productdomain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutPreviewResponse {
    private String previewToken;
    private LocalDateTime expiresAt;
    private List<CheckoutPreviewItemResponse> errorItems;
}
