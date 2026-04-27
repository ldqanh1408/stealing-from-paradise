package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findAllByIsActiveTrueOrderBySortOrderAscNameAsc();
}
