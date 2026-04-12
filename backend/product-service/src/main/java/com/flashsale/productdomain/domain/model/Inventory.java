package com.flashsale.productdomain.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "inventories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    @Id
    private String id;

    @Indexed(unique = true)
    private String skuCode;

    private Long productId;
    private Integer stockAvailable;
    private Integer stockLocked;
    private Integer stockFlashReserved;

    // dùng MongoTemplate $inc thay vì save() để tránh Lost Update
}

