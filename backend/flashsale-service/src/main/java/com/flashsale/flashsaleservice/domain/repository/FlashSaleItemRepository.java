package com.flashsale.flashsaleservice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {
    List<FlashSaleItem> findBySessionId(Long sessionId);
    Optional<FlashSaleItem> findBySkuCode(String skuCode);
}

