package com.flashsale.identitydomain.domain.repository;

import com.flashsale.identitydomain.domain.model.TrustScoreEventsConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TrustScoreEventsConfigRepository extends JpaRepository<TrustScoreEventsConfig, Long> {
    Optional<TrustScoreEventsConfig> findByEventCode(String eventCode);
}
