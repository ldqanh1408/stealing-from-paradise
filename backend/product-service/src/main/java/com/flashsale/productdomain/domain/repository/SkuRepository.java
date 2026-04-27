package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.Sku;
import com.flashsale.productdomain.domain.model.SkuStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SkuRepository extends JpaRepository<Sku, UUID> {
    List<Sku> findByProductId(UUID productId);

    List<Sku> findByIdIn(Collection<UUID> ids);

    List<Sku> findByStatus(SkuStatus status);

    @Modifying
    @Query("""
        update Sku s
        set s.stockQuantity = s.stockQuantity - :qty,
            s.version = s.version + 1
        where s.id = :skuId
          and s.stockQuantity >= :qty
          and s.version = :version
        """)
    int decrementStock(@Param("skuId") UUID skuId, @Param("qty") int qty, @Param("version") int version);

    @Modifying
    @Query("""
        update Sku s
        set s.stockQuantity = s.stockQuantity + :qty,
            s.version = s.version + 1
        where s.id = :skuId
        """)
    int incrementStock(@Param("skuId") UUID skuId, @Param("qty") int qty);

    @Modifying
    @Query("""
        update Sku s
        set s.stockQuantity = :stock,
            s.version = s.version + 1
        where s.id = :skuId
        """)
    int setStock(@Param("skuId") UUID skuId, @Param("stock") int stock);
}
