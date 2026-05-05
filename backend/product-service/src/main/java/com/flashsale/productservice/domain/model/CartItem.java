package com.flashsale.productservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MG_CART_ITEMS - Standalone MongoDB collection
 * Mỗi item trong giỏ hàng được lưu riêng thay vì embedded trong Cart document
 * Này giúp query/update item độc lập mà không phải load toàn bộ giỏ hàng
 */
@Document(collection = "cart_items")
@CompoundIndex(name = "idx_cart_user", def = "{'cart_id': 1, 'user_id': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    @Id
    private String id;

    @Indexed
    private String cartId;  // FK -> MG_CARTS._id

    @Indexed
    private Long userId;  // FK -> USERS.id (denormalized for fast query)

    private String variantId;  // Logic ID của variant

    @Indexed
    private String skuCode;  // Mã SKU

    private Long fsItemId;  // FK -> FS_ITEMS.id (nullable, nếu từ Flash Sale)

    private BigDecimal priceSnapshot;  // Giá tại thời điểm thêm vào giỏ (đặc phòng giảm giá)

    private Integer quantity;  // Số lượng

    private LocalDateTime addedAt;  // Thời điểm thêm vào giỏ
}

