package com.flashsale.productdomain.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productdomain.domain.model.Category;
import com.flashsale.productdomain.domain.repository.CategoryRepository;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.dto.request.CreateCategoryRequest;
import com.flashsale.productdomain.dto.request.UpdateCategoryRequest;
import com.flashsale.productdomain.dto.response.CategoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from).toList();
    }

    public CategoryResponse createCategory(CreateCategoryRequest req) {
        if (categoryRepository.findBySlug(req.getSlug()).isPresent()) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Slug đã tồn tại: " + req.getSlug());
        }

        Category category = Category.builder()
                .name(req.getName())
                .slug(req.getSlug())
                .parentId(req.getParentId())
                .level(req.getLevel())
                .build();

        return CategoryResponse.from(categoryRepository.save(category));
    }

    public CategoryResponse updateCategory(String categoryId, UpdateCategoryRequest req) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại"));

        if (req.getSlug() != null && !req.getSlug().equals(category.getSlug())) {
            if (categoryRepository.findBySlug(req.getSlug()).isPresent()) {
                throw new AppException(ErrorCode.ALREADY_EXISTS, "Slug đã tồn tại: " + req.getSlug());
            }
            category.setSlug(req.getSlug());
        }

        if (req.getName() != null)     category.setName(req.getName());
        if (req.getParentId() != null) category.setParentId(req.getParentId());
        if (req.getLevel() != null)    category.setLevel(req.getLevel());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    public void deleteCategory(String categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại"));

        // Block if it has sub-categories
        if (!categoryRepository.findByParentId(categoryId).isEmpty()) {
            throw new AppException(ErrorCode.ALREADY_EXISTS,
                    "Danh mục đang có sub-category, không thể xóa");
        }

        // Block if it has active products
        long productCount = productRepository.countBySellerIdAndStatusAndDeletedAtIsNull(null, "PUBLISHED");
        // Just check for products referencing this category
        boolean hasProducts = productRepository.findByStatusAndCategoryIdAndDeletedAtIsNull(
                        "PUBLISHED", categoryId,
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .hasContent();
        if (hasProducts) {
            throw new AppException(ErrorCode.ALREADY_EXISTS,
                    "Danh mục đang có sản phẩm, không thể xóa");
        }

        categoryRepository.deleteById(categoryId);
    }
}
