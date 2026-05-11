# UC-PRODUCT-001: Browse Catalog

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-001 |
| **Actor** | Customer (Public) |
| **Priority** | HIGH |
| **Precondition** | None |
| **Postcondition** | Customer views products filtered by category and/or search criteria |

---

## Main Flow

```
1. Customer navigates to homepage or category page
2. System returns category tree via GET /categories
   - Only is_active=TRUE categories shown
   - Sorted by sort_order ASC
3. Customer selects a category or searches
4. System returns product list via GET /products
   - Filters: category_id, status (active/out_of_stock only)
   - Each product card shows:
     * Thumbnail image (product_image WHERE variant_id IS NULL AND sort_order=MIN)
     * Product name
     * Min price across active variants ("Tu X d")
     * Sold count (from Elasticsearch)
     * Discount badge if any variant has price < original_price
5. Customer clicks a product
6. System returns product detail via GET /products/{id}
   - Full variant matrix (active + out_of_stock; inactive excluded)
   - Image gallery (common + variant-specific)
   - Attributes table (Tab "Chi tiet")
   - Description HTML (Tab "Mo ta")
   - Price display per selected variant
```

---

## Variant Selection Sub-Flow

```
1. System groups all variants by variant_attributes keys
2. Customer selects attribute values (e.g., color=Den, size=M)
3. System maps combination to specific variant
4. IF variant is active and stock > 0:
   - Show price, enable "Them vao gio hang" and "Mua ngay" buttons
5. IF variant is out_of_stock:
   - Show "Het hang" button (disabled)
6. IF variant is inactive:
   - Option not shown at all
```

---

## Related Endpoints

| Endpoint | Usage |
|----------|-------|
| GET /categories | Category tree (step 2) |
| GET /categories/{categoryId} | Single category detail with breadcrumb and children |
| GET /products | Product listing with filters (step 4) |
| GET /products/{id} | Product detail with variants and images (step 6) |

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-001 | Browse category tree |
| FR-PRODUCT-005 | List/search products |
| FR-PRODUCT-006 | Product detail view |
| BR-PRODUCT-001 | Category hierarchy constraints |
| ENTITY-PRODUCT-001 | CATEGORY |
| ENTITY-PRODUCT-002 | PRODUCT |
| ENTITY-PRODUCT-003 | PRODUCT_VARIANT |
| ENTITY-PRODUCT-004 | PRODUCT_IMAGE |
