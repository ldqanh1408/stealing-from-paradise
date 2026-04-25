package com.flashsale.identitydomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.identitydomain.dto.request.AddressCreateRequest;
import com.flashsale.identitydomain.dto.request.AddressUpdateRequest;
import com.flashsale.identitydomain.dto.request.UserProfileUpdateRequest;
import com.flashsale.identitydomain.dto.response.AddressResponse;
import com.flashsale.identitydomain.dto.response.PresignedUrlResponse;
import com.flashsale.identitydomain.dto.response.TrustScoreLogResponse;
import com.flashsale.identitydomain.dto.response.UserProfileResponse;
import com.flashsale.identitydomain.service.TrustScoreService;
import com.flashsale.identitydomain.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final TrustScoreService trustScoreService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserProfile(user.getId())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateCurrentUser(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateUserProfile(user.getId(), request), "Profile updated"));
    }

    @GetMapping("/me/avatar/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getAvatarPresignedUrl(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam String contentType) {
        String objectKey = user.getId() + "/" + java.util.UUID.randomUUID() + getExtension(contentType);
        String cdnUrl = "https://cdn.marketplace.vn/avatars/" + objectKey;

        PresignedUrlResponse response = PresignedUrlResponse.builder()
                .uploadUrl(buildPresignedPutUrl(objectKey, contentType))
                .objectKey(objectKey)
                .cdnUrl(cdnUrl)
                .expiresIn(900)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me/addresses")
    public ResponseEntity<ApiResponse<java.util.List<AddressResponse>>> getAddresses(
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserAddresses(user.getId())));
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody AddressCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.addAddress(user.getId(), request), "Address added"));
    }

    @PutMapping("/me/addresses/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable Long addressId,
            @RequestBody AddressUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateAddress(user.getId(), addressId, request), "Address updated"));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable Long addressId) {
        userService.deleteAddress(user.getId(), addressId);
        return ResponseEntity.ok(ApiResponse.success(null, "Address deleted"));
    }

    @PostMapping("/me/roles/seller")
    public ResponseEntity<ApiResponse<Void>> registerAsSeller(
            @AuthenticationPrincipal UserDetailsImpl user) {
        userService.registerAsSeller(user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Registered as seller"));
    }

    @GetMapping("/me/trust-score/logs")
    public ResponseEntity<ApiResponse<PageResponse<TrustScoreLogResponse>>> getTrustScoreLogs(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(trustScoreService.getUserTrustScoreLogs(user.getId(), pageable))));
    }

    private String getExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String buildPresignedPutUrl(String objectKey, String contentType) {
        return "https://minio.internal/user-avatars/" + objectKey + "?X-Amz-Algorithm=AWS4-HMAC-SHA256";
    }
}
