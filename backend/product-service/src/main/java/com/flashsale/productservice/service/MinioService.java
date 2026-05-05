package com.flashsale.productservice.service;

import com.flashsale.productservice.dto.response.PresignedUrlResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private static final String BUCKET = "products-media";
    private static final int PRESIGNED_TTL_SECONDS = 900; // 15 minutes

    private final MinioClient minioClient;

    @Value("${minio.url}")
    private String minioUrl;

    /**
     * Generates a pre-signed PUT URL for uploading a product image.
     * Path: products/{sellerId}/{productId}/{uuid}.{ext}
     */
    public PresignedUrlResponse generatePresignedUrl(Long sellerId, String productId,
                                                      String fileName, String contentType) {
        String ext = extractExtension(fileName);
        String objectKey = String.format("products/%d/%s/%s.%s",
                sellerId, productId, UUID.randomUUID(), ext);

        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(BUCKET)
                            .object(objectKey)
                            .expiry(PRESIGNED_TTL_SECONDS, TimeUnit.SECONDS)
                            .extraQueryParams(Map.of("Content-Type", contentType))
                            .build()
            );

            String objectUrl = buildObjectUrl(objectKey);

            return new PresignedUrlResponse(presignedUrl, objectUrl, PRESIGNED_TTL_SECONDS);
        } catch (Exception e) {
            log.error("Failed to generate pre-signed URL for object {}: {}", objectKey, e.getMessage());
            throw new RuntimeException("Không thể tạo URL upload ảnh", e);
        }
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0) ? fileName.substring(dot + 1).toLowerCase() : "jpg";
    }

    private String buildObjectUrl(String objectKey) {
        // CDN URL pattern used in the project
        return "https://cdn.marketplace.vn/" + BUCKET + "/" + objectKey;
    }
}
