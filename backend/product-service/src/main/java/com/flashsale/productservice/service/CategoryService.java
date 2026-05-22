package com.flashsale.productservice.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.domain.model.Category;
import com.flashsale.productservice.domain.repository.CategoryRepository;
import com.flashsale.productservice.domain.repository.ProductRepository;
import com.flashsale.productservice.dto.request.CreateCategoryRequest;
import com.flashsale.productservice.dto.request.UpdateCategoryRequest;
import com.flashsale.productservice.dto.response.CategoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    /**
     * Build hierarchical category tree from flat list.
     * Only active categories (isActive=true) are included.
     * Children are recursively built and sorted by sortOrder.
     */
    public List<CategoryResponse> buildCategoryTree(List<Category> allCategories) {
        Map<String, List<Category>> childrenMap = allCategories.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        List<Category> rootCategories = allCategories.stream()
                .filter(c -> c.getParentId() == null && Boolean.TRUE.equals(c.getIsActive()))
                .sorted((a, b) -> {
                    int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
                    int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
                    return Integer.compare(orderA, orderB);
                })
                .toList();

        return rootCategories.stream()
                .map(root -> buildCategoryNode(root, childrenMap))
                .toList();
    }

    private CategoryResponse buildCategoryNode(Category category, Map<String, List<Category>> childrenMap) {
        List<Category> children = childrenMap.getOrDefault(category.getId(), new ArrayList<>());

        List<CategoryResponse> childResponses = children.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .sorted((a, b) -> {
                    int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
                    int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
                    return Integer.compare(orderA, orderB);
                })
                .map(child -> buildCategoryNode(child, childrenMap))
                .toList();

        CategoryResponse response = CategoryResponse.from(category);
        response.setChildren(childResponses.isEmpty() ? null : childResponses);
        return response;
    }

    /**
     * Return hierarchical tree of all active categories.
     */
    public List<CategoryResponse> getAllCategories() {
        List<Category> allCategories = categoryRepository.findAll();
        return buildCategoryTree(allCategories);
    }

    /**
     * Create a new category.
     * Sets isActive=true by default, sortOrder=0 if not provided.
     */
    public CategoryResponse createCategory(CreateCategoryRequest req) {
        if (categoryRepository.findBySlug(req.getSlug()).isPresent()) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Slug đã tồn tại: " + req.getSlug());
        }

        Category category = Category.builder()
                .name(req.getName())
                .slug(req.getSlug())
                .parentId(req.getParentId())
                .level(req.getLevel())
                .description(req.getDescription())
                .imageUrl(req.getImageUrl())
                .isActive(true)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .build();

        return CategoryResponse.from(categoryRepository.save(category));
    }

    /**
     * Update an existing category.
     * Handles isActive and sortOrder updates.
     */
    public CategoryResponse updateCategory(String categoryId, UpdateCategoryRequest req) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại"));

        if (req.getSlug() != null && !req.getSlug().equals(category.getSlug())) {
            if (categoryRepository.findBySlug(req.getSlug()).isPresent()) {
                throw new AppException(ErrorCode.ALREADY_EXISTS, "Slug đã tồn tại: " + req.getSlug());
            }
            category.setSlug(req.getSlug());
        }

        if (req.getName() != null)               category.setName(req.getName());
        if (req.getParentId() != null)           category.setParentId(req.getParentId());
        if (req.getLevel() != null)              category.setLevel(req.getLevel());
        if (req.getDescription() != null)        category.setDescription(req.getDescription());
        if (req.getImageUrl() != null)           category.setImageUrl(req.getImageUrl());
        if (req.getIsActive() != null)           category.setIsActive(req.getIsActive());
        if (req.getSortOrder() != null)          category.setSortOrder(req.getSortOrder());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    /**
     * Delete a category.
     * Only allows deletion if:
     * - isActive=true
     * - No sub-categories
     * - No published products
     */
    public void deleteCategory(String categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại"));

        // Only allow deleting active categories
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS,
                    "Danh mục đang không hoạt động, không thể xóa");
        }

        // Block if it has sub-categories
        List<Category> subCategories = categoryRepository.findByParentId(categoryId);
        if (!subCategories.isEmpty()) {
            // Only count active sub-categories as blocking
            long activeSubCount = subCategories.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                    .count();
            if (activeSubCount > 0) {
                throw new AppException(ErrorCode.ALREADY_EXISTS,
                        "Danh mục đang có danh mục con đang hoạt động, không thể xóa");
            }
        }

        // Block if it has published products
        boolean hasProducts = productRepository.findByStatusAndCategoryIdAndDeletedAtIsNull(
                        "PUBLISHED", categoryId,
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .hasContent();
        if (hasProducts) {
            throw new AppException(ErrorCode.ALREADY_EXISTS,
                    "Danh mục đang có sản phẩm, không thể xóa");
        }

        // Soft-delete: mark as inactive instead of hard delete
        category.setIsActive(false);
        categoryRepository.save(category);
        log.info("Soft-deleted category: id={}", categoryId);
    }
}
