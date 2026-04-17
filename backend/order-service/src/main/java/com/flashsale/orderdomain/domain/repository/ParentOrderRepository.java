package com.flashsale.orderdomain.domain.repository;

import com.flashsale.orderdomain.domain.model.ParentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParentOrderRepository extends JpaRepository<ParentOrder, Long> {

    Optional<ParentOrder> findByIdAndUserId(Long id, Long userId);

    Optional<ParentOrder> findByOrderCode(String orderCode);

    @Query("SELECT po FROM ParentOrder po LEFT JOIN FETCH po.orders WHERE po.id = :id AND po.userId = :userId")
    Optional<ParentOrder> findByIdAndUserIdWithOrders(Long id, Long userId);
}
