package com.flashsale.identityservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long userId;
    private String username;
    private String email;
    private String phone;
    private String fullName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
