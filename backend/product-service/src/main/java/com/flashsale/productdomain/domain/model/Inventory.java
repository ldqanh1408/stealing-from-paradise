package com.flashsale.productdomain.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * MG_INVENTORIES - Quản lý tồn kho
 *
 * IMPORTANT: Mọi thao tác tăng/giảm stock BẮT BUỘC dùng MongoDB $inc atomic
 * thay vì fetch-then-update để tránh Lost Update khi nhiều thread cùng ghi.
 *
 * ⚠️ KHÔNG CÓ trường version - phải dùng $inc operator thay vì optimistic locking
 */
@Document(collection = "inventories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    @Id
    private String id;

    @Indexed(unique = true)
    private String skuCode;  // 1:1 link với MG_PRODUCT_VARIANTS

    private String productId;  // FK -> MG_PRODUCTS._id

    private Integer stockTotal;  // Tổng tồn kho

    private Integer stockLocked;  // Số lượng đang giữ chỗ (đơn PENDING/PAID)

    private Integer stockAvailable;  // Số lượng còn có thể bán

    private Integer stockFlashReserved;  // Số lượng đã khóa cho Flash Sale
                                         // Được trừ khỏi stock_available ngay khi
                                         // Admin APPROVE FS_ITEM, hoàn trả phần
                                         // chưa bán sau khi session ENDED

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

