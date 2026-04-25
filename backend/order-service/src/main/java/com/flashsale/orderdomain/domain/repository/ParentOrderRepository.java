package com.flashsale.orderdomain.domain.repository;

import com.flashsale.orderdomain.domain.model.ParentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParentOrderRepository extends JpaRepository<ParentOrder, Long> {

    Optional<ParentOrder> findByIdAndUserId(Long id, Long userId);

    Optional<ParentOrder> findByOrderCode(String orderCode);

    /**
     * Pessimistic lock on ParentOrder during payment confirmation/failure.
     * Required because ParentOrder has @Version (optimistic locking) and may be
     * modified concurrently by other transactions (e.g. payment timeout, another
     * saga instance). Without this lock, an ObjectOptimisticLockingFailureException
     * is thrown when the saga's transaction commits after another transaction has
     * already incremented the version.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM ParentOrder po WHERE po.id = :id")
    Optional<ParentOrder> findByIdWithPessimisticLock(Long id);

    @Query("SELECT po FROM ParentOrder po LEFT JOIN FETCH po.orders WHERE po.id = :id AND po.userId = :userId")
    Optional<ParentOrder> findByIdAndUserIdWithOrders(Long id, Long userId);
}
