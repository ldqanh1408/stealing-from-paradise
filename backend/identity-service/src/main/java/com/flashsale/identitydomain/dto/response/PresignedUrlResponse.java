package com.flashsale.identitydomain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {
    private String uploadUrl;
    private String objectKey;
    private String cdnUrl;
    private Integer expiresIn;
}
