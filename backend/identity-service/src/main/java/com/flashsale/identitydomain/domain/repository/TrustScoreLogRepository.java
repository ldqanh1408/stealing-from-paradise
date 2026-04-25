package com.flashsale.identitydomain.domain.repository;

import com.flashsale.identitydomain.domain.model.TrustScoreLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TrustScoreLogRepository extends JpaRepository<TrustScoreLog, Long> {
    Page<TrustScoreLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT COUNT(l) FROM TrustScoreLog l WHERE l.userId = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE User u SET u.trustScore = u.trustScore + :delta WHERE u.id = :userId")
    int updateTrustScore(@Param("userId") Long userId, @Param("delta") Integer delta);
}
