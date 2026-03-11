package com.onlinestore.catalog.api;

import com.onlinestore.catalog.dto.CategoryDTO;
import com.onlinestore.catalog.dto.CategoryWithProductsDTO;
import com.onlinestore.catalog.dto.CreateProductRequest;
import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.dto.ProductFilterRequest;
import com.onlinestore.catalog.dto.UpdateProductRequest;
import com.onlinestore.catalog.service.CategoryService;
import com.onlinestore.catalog.service.ProductService;
import com.onlinestore.common.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final ProductService productService;
    private final CategoryService categoryService;

    @GetMapping("/api/v1/public/catalog/categories")
    public List<CategoryDTO> getCategories() {
        return categoryService.getCategoryTree();
    }

    @GetMapping("/api/v1/public/catalog/categories/{slug}")
    public CategoryWithProductsDTO getCategoryBySlug(@PathVariable String slug, Pageable pageable) {
        return categoryService.getBySlugWithProducts(slug, pageable);
    }

    @GetMapping("/api/v1/public/catalog/products")
    public PageResponse<ProductDTO> getProducts(@Valid ProductFilterRequest filter, Pageable pageable) {
        return productService.findAll(filter, pageable);
    }

    @GetMapping("/api/v1/public/catalog/products/{slug}")
    public ProductDTO getProductBySlug(@PathVariable String slug) {
        return productService.findBySlug(slug);
    }

    @PostMapping("/api/admin/catalog/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProductDTO createProduct(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/api/admin/catalog/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProductDTO updateProduct(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }
}
