package com.onlinestore.catalog.service;

import com.onlinestore.catalog.dto.CreateProductRequest;
import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.dto.ProductFilterRequest;
import com.onlinestore.catalog.dto.UpdateProductRequest;
import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductStatus;
import com.onlinestore.catalog.mapper.ProductMapper;
import com.onlinestore.catalog.repository.CategoryRepository;
import com.onlinestore.catalog.repository.ProductRepository;
import com.onlinestore.catalog.repository.ProductSpecification;
import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.common.dto.PageResponse;
import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.common.util.SlugGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final RabbitTemplate rabbitTemplate;
    private final SlugGenerator slugGenerator;

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#slug")
    public ProductDTO findBySlug(String slug) {
        return productRepository.findWithDetailsBySlug(slug)
            .map(productMapper::toDto)
            .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDTO> findAll(ProductFilterRequest filter, Pageable pageable) {
        Specification<Product> spec = Specification.where(
            ProductSpecification.hasStatus(ProductStatus.ACTIVE)
        );
        if (filter.categoryId() != null) {
            spec = spec.and(ProductSpecification.inCategory(filter.categoryId()));
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            spec = spec.and(ProductSpecification.nameContains(filter.query()));
        }
        if (filter.priceMin() != null || filter.priceMax() != null) {
            spec = spec.and(ProductSpecification.priceRange(filter.priceMin(), filter.priceMax()));
        }
        var page = productRepository.findAll(spec, pageable);
        List<Product> productsWithDetails = loadProductsWithDetails(page.getContent());
        var dtoContent = productsWithDetails.stream()
            .map(productMapper::toDto)
            .toList();
        var dtoPage = new PageImpl<>(dtoContent, pageable, page.getTotalElements());
        return PageResponse.of(dtoPage);
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProductDTO create(CreateProductRequest request) {
        var product = productMapper.toEntity(request);
        String slug = slugGenerator.generate(request.name());
        if (productRepository.existsBySlug(slug)) {
            slug = slug + "-" + System.currentTimeMillis();
        }
        product.setSlug(slug);
        product.setStatus(ProductStatus.ACTIVE);
        if (request.categoryId() != null) {
            var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.categoryId()));
            product.setCategory(category);
        }
        var saved = productRepository.save(product);
        ProductDTO dto = productMapper.toDto(saved);
        publishEvent("product.created", dto);
        return dto;
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProductDTO update(Long id, UpdateProductRequest request) {
        var product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        productMapper.updateEntity(product, request);
        if (request.categoryId() != null) {
            var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.categoryId()));
            product.setCategory(category);
        }
        var saved = productRepository.save(product);
        ProductDTO dto = productMapper.toDto(saved);
        publishEvent("product.updated", dto);
        return dto;
    }

    private void publishEvent(String routingKey, ProductDTO product) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.PRODUCT_EXCHANGE, routingKey, product);
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
