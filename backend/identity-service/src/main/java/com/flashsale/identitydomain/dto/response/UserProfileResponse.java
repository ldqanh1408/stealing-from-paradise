package com.flashsale.identitydomain.dto.response;

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
public class UserProfileResponse {
    private Long userId;
    private String username;
    private String email;
    private String phone;
    private String fullName;
    private String avatarUrl;
    private List<String> roles;
    private String status;
    private Integer trustScore;
    private String trustTier;
    private Integer appealCount;
    private Boolean productPostingSuspended;
    private String lockReason;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
