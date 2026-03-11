package com.onlinestore.catalog.service;

import com.onlinestore.catalog.dto.CategoryDTO;
import com.onlinestore.catalog.dto.CategoryWithProductsDTO;
import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.mapper.ProductMapper;
import com.onlinestore.catalog.repository.CategoryRepository;
import com.onlinestore.catalog.repository.ProductRepository;
import com.onlinestore.common.dto.PageResponse;
import com.onlinestore.common.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "categories", key = "'tree'")
    public List<CategoryDTO> getCategoryTree() {
        return productMapper.toCategoryDtos(categoryRepository.findByParentIsNullAndActiveTrueOrderBySortOrderAsc());
    }

    @Transactional(readOnly = true)
    public CategoryWithProductsDTO getBySlugWithProducts(String slug, Pageable pageable) {
        var category = categoryRepository.findBySlugAndActiveTrue(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
        var productsPage = productRepository.findActiveByCategory(category.getId(), pageable);
        List<Product> productsWithDetails = loadProductsWithDetails(productsPage.getContent());
        var dtoContent = productsWithDetails.stream()
            .map(productMapper::toDto)
            .toList();
        var dtoPage = new PageImpl<>(dtoContent, pageable, productsPage.getTotalElements());
        PageResponse<ProductDTO> products = PageResponse.of(dtoPage);
        return new CategoryWithProductsDTO(productMapper.toCategoryDto(category), products);
    }

    private List<Product> loadProductsWithDetails(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream()
            .map(Product::getId)
            .toList();
        List<Product> detailedProducts = productRepository.findAllWithDetailsByIdIn(productIds);
        Map<Long, Product> detailedById = new LinkedHashMap<>();
        for (Product product : detailedProducts) {
            detailedById.put(product.getId(), product);
        }

        return productIds.stream()
            .map(detailedById::get)
            .filter(Objects::nonNull)
            .toList();
    }
}
