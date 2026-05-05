package com.flashsale.productservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class PresignedUrlResponse {
    private String presignedUrl;
    private String objectUrl;
    private int expiresIn;
}
