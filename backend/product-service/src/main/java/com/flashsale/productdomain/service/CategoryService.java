package com.flashsale.productdomain.service;

import com.flashsale.productdomain.domain.model.Category;
import com.flashsale.productdomain.domain.repository.CategoryRepository;
import com.flashsale.productdomain.dto.response.CategoryResponse;
import com.flashsale.productdomain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final String CATEGORY_CACHE_KEY = "categories:all:active";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final CategoryRepository categoryRepository;
    private final StringRedisTemplate redisTemplate;

    public List<CategoryResponse> getAllActiveCategories() {
        // Try to get from cache first
        // For simplicity, we fetch from DB and cache the result
        List<Category> allCategories = categoryRepository.findAllByIsActiveTrueOrderBySortOrderAscNameAsc();

        // Build tree structure
        return buildCategoryTree(allCategories);
    }

    public CategoryResponse getCategoryById(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        return mapToResponse(category);
    }

    public List<CategoryResponse> getCategoryTree() {
        List<Category> allCategories = categoryRepository.findAllByIsActiveTrueOrderBySortOrderAscNameAsc();
        return buildCategoryTree(allCategories);
    }

    private List<CategoryResponse> buildCategoryTree(List<Category> allCategories) {
        // Create a map for quick lookup
        Map<UUID, CategoryResponse> categoryMap = allCategories.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toMap(CategoryResponse::getId, c -> c));

        // Build tree structure
        List<CategoryResponse> rootCategories = new ArrayList<>();

        for (Category category : allCategories) {
            CategoryResponse response = categoryMap.get(category.getId());

            if (category.getParent() == null) {
                // This is a root category
                rootCategories.add(response);
            } else {
                // This is a child category
                CategoryResponse parentResponse = categoryMap.get(category.getParent().getId());
                if (parentResponse != null) {
                    if (parentResponse.getSubCategories() == null) {
                        parentResponse.setSubCategories(new ArrayList<>());
                    }
                    parentResponse.getSubCategories().add(response);
                }
            }
        }

        return rootCategories;
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .sortOrder(category.getSortOrder())
                .isActive(category.getIsActive())
                .subCategories(new ArrayList<>())
                .build();
    }

    public void invalidateCache() {
        // Invalidate cache when categories change
        redisTemplate.delete(CATEGORY_CACHE_KEY);
        log.info("Category cache invalidated");
    }
}
