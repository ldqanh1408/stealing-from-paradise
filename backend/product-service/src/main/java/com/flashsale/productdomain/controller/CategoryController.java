package com.flashsale.productdomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.productdomain.dto.request.CreateCategoryRequest;
import com.flashsale.productdomain.dto.request.UpdateCategoryRequest;
import com.flashsale.productdomain.dto.response.CategoryResponse;
import com.flashsale.productdomain.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    // ─── Public: GET /categories ──────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAllCategories()));
    }

    // ─── Admin: POST /admin/categories ───────────────────────────────────────

    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest req) {
        CategoryResponse created = categoryService.createCategory(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    // ─── Admin: PUT /admin/categories/{categoryId} ────────────────────────────

    @PutMapping("/admin/categories/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable String categoryId,
            @Valid @RequestBody UpdateCategoryRequest req) {
        CategoryResponse updated = categoryService.updateCategory(categoryId, req);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    // ─── Admin: DELETE /admin/categories/{categoryId} ─────────────────────────

    @DeleteMapping("/admin/categories/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable String categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa danh mục thành công"));
    }
}
