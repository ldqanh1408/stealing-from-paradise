package com.flashsale.identitydomain.domain.repository;

import com.flashsale.identitydomain.domain.model.UserBanHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserBanHistoryRepository extends JpaRepository<UserBanHistory, Long> {
    List<UserBanHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
