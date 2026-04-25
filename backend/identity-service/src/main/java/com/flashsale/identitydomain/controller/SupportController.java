package com.flashsale.identitydomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.identitydomain.dto.request.AppealCreateRequest;
import com.flashsale.identitydomain.dto.response.AppealCreatedResponse;
import com.flashsale.identitydomain.dto.response.AppealPresignedUrlResponse;
import com.flashsale.identitydomain.exception.AppealLimitExceededException;
import com.flashsale.identitydomain.service.AppealService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/support")
@RequiredArgsConstructor
@Slf4j
public class SupportController {

    private final AppealService appealService;

    @GetMapping("/trust-score-appeal/presigned-url")
    public ResponseEntity<ApiResponse<AppealPresignedUrlResponse>> getAppealPresignedUrl(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam String fileName,
            @RequestParam String contentType) {
        String ext = getExtension(contentType, fileName);
        String objectKey = "appeals/" + user.getId() + "/" + java.util.UUID.randomUUID() + ext;
        String cdnUrl = "https://cdn.marketplace.vn/appeal-evidence/" + objectKey;

        AppealPresignedUrlResponse response = AppealPresignedUrlResponse.builder()
                .presignedUrl("https://minio.internal/appeal-evidence/" + objectKey + "?X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .objectUrl(cdnUrl)
                .expiresIn(900)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/trust-score-appeal")
    public ResponseEntity<ApiResponse<AppealCreatedResponse>> submitAppeal(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody AppealCreateRequest request) {
        try {
            var appeal = appealService.createAppeal(
                    user.getId(),
                    request.getLogId(),
                    request.getReason(),
                    request.getEvidenceUrls()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(
                            AppealCreatedResponse.builder()
                                    .appealId(appeal.getId())
                                    .status(appeal.getStatus())
                                    .createdAt(appeal.getCreatedAt())
                                    .build(),
                            "Appeal submitted"));
        } catch (AppealLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("APPEAL_LIMIT", e.getMessage()));
        } catch (RuntimeException e) {
            HttpStatus status = e.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error("APPEAL_BAD", e.getMessage()));
        }
    }

    @GetMapping("/trust-score-appeal")
    public ResponseEntity<ApiResponse<java.util.List<Map<String, Object>>>> getMyAppeals(
            @AuthenticationPrincipal UserDetailsImpl user) {
        var appeals = appealService.getUserAppeals(user.getId()).stream()
                .map(a -> Map.<String, Object>of(
                        "appealId", (Object) a.getId(),
                        "trustScoreLogId", a.getTrustScoreLogId(),
                        "reason", a.getReason(),
                        "status", a.getStatus(),
                        "adminNote", a.getAdminNote() != null ? a.getAdminNote() : "",
                        "reviewedAt", a.getReviewedAt() != null ? a.getReviewedAt() : "",
                        "createdAt", a.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(appeals));
    }

    private String getExtension(String contentType, String fileName) {
        if (fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf("."));
            if (ext.matches("\\.(jpg|jpeg|png|webp)")) return ext;
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
