package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {
    Optional<Category> findBySlug(String slug);

    List<Category> findByParentId(String parentId);

    List<Category> findByParentIdIsNull();  // Root categories

    List<Category> findByLevel(Integer level);
}

