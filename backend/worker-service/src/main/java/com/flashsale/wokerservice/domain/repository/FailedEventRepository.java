package com.flashsale.wokerservice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.wokerservice.domain.model.FailedEvent;
import java.util.List;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {
    List<FailedEvent> findByStatus(String status);
}

