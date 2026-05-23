package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.productservice.dto.category.CategoryResponse;
import com.flashsale.productservice.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategoryTree() {
        return ResponseEntity.ok(categoryService.getCategoryTree());
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryDetail(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(categoryService.getCategoryDetail(categoryId));
    }
}
