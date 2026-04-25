package com.flashsale.identitydomain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.identitydomain.domain.model.Appeal;
import java.util.List;

@Repository
public interface AppealRepository extends JpaRepository<Appeal, Long> {
    List<Appeal> findByUserId(Long userId);
    List<Appeal> findByStatus(String status);
    Page<Appeal> findByStatus(String status, Pageable pageable);
}

