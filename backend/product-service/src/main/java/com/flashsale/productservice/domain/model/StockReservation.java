package com.flashsale.productservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * ENTITY-PRODUCT-005: Stock Reservation
 * Lưu trữ thông tin đặt chỗ tồn kho trong quá trình checkout
 * Đặt chỗ sẽ hết hạn sau 15 phút nếu không được confirm
 */
@Document(collection = "stock_reservations")
@CompoundIndex(name = "idx_cleanup", def = "{'status': 1, 'expires_at': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservation {

    @Id
    private String id;

    @Indexed
    private String variantId;

    @Indexed
    private String skuCode;

    @Indexed
    private String sessionId;  // Checkout session ID

    private Integer quantity;  // Số lượng đặt chỗ

    private String status;  // pending/confirmed/released

    @Indexed
    private LocalDateTime expiresAt;  // NOW() + 15 minutes

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Trạng thái đặt chỗ tồn kho
     */
    public enum ReservationStatus {
        PENDING,
        CONFIRMED,
        RELEASED
    }
}
