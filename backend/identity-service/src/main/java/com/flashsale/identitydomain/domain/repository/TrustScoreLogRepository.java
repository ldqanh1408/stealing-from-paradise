package com.flashsale.identitydomain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.identitydomain.domain.model.TrustScoreLog;
import java.util.List;

@Repository
public interface TrustScoreLogRepository extends JpaRepository<TrustScoreLog, Long> {
    List<TrustScoreLog> findByUserId(Long userId);
}

