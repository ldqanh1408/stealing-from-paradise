package com.flashsale.identitydomain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppealPresignedUrlResponse {
    private String presignedUrl;
    private String objectUrl;
    private Integer expiresIn;
}
