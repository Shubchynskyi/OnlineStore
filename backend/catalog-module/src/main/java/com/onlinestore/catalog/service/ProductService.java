package com.onlinestore.catalog.service;

import com.onlinestore.catalog.dto.AttachImageRequest;
import com.onlinestore.catalog.dto.CreateProductRequest;
import com.onlinestore.catalog.dto.ImageDTO;
import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.dto.ProductFilterRequest;
import com.onlinestore.catalog.dto.ProductAttributeRequest;
import com.onlinestore.catalog.dto.UpdateProductRequest;
import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductImage;
import com.onlinestore.catalog.entity.ProductStatus;
import com.onlinestore.catalog.mapper.ProductMapper;
import com.onlinestore.catalog.repository.CategoryRepository;
import com.onlinestore.catalog.repository.ProductRepository;
import com.onlinestore.catalog.repository.ProductSpecification;
import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.common.dto.PageResponse;
import com.onlinestore.common.event.OutboxService;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.common.util.SlugGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final OutboxService outboxService;
    private final SlugGenerator slugGenerator;
    private final MediaStorageService mediaStorageService;
    private final TransactionTemplate transactionTemplate;

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
        ensureUniqueAttributeNames(request.attributes());
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
        ensureUniqueAttributeNames(request.attributes());
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

    @CacheEvict(value = "products", allEntries = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ImageDTO attachImage(Long id, AttachImageRequest request) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product", "id", id);
        }
        if (productRepository.existsImageByObjectKey(request.objectKey())) {
            throw new BusinessException(
                "PRODUCT_IMAGE_ALREADY_ATTACHED",
                "Media object is already attached to a product: " + request.objectKey()
            );
        }
        mediaStorageService.assertObjectIsAttachable(request.objectKey());
        return Objects.requireNonNull(transactionTemplate.execute(status -> attachImageTransactional(id, request)));
    }

    private void publishEvent(String routingKey, ProductDTO product) {
        outboxService.queueEvent(RabbitMQConfig.PRODUCT_EXCHANGE, routingKey, product);
    }

    private int resolveSortOrder(Product product, Integer requestedSortOrder) {
        if (requestedSortOrder != null) {
            return requestedSortOrder;
        }
        return product.getImages().stream()
            .map(ProductImage::getSortOrder)
            .max(Integer::compareTo)
            .orElse(-1) + 1;
    }

    private boolean isDuplicateObjectKeyViolation(DataIntegrityViolationException ex) {
        Throwable mostSpecificCause = NestedExceptionUtils.getMostSpecificCause(ex);
        String message = mostSpecificCause == null ? ex.getMessage() : mostSpecificCause.getMessage();
        return message != null && message.contains("ux_product_images_object_key");
    }

    private ImageDTO attachImageTransactional(Long id, AttachImageRequest request) {
        var product = productRepository.findWithDetailsById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        boolean makeMain = request.isMain() || product.getImages().isEmpty();
        if (makeMain) {
            product.getImages().forEach(image -> image.setMain(false));
        }

        var image = new ProductImage();
        image.setProduct(product);
        image.setObjectKey(request.objectKey());
        String resolvedUrl = mediaStorageService.resolveObjectUrl(request.objectKey());
        if (resolvedUrl.length() > MediaStorageService.MAX_PRODUCT_IMAGE_URL_LENGTH) {
            throw new BusinessException(
                "PRODUCT_IMAGE_URL_TOO_LONG",
                "Resolved image URL exceeds the supported length for stored media metadata"
            );
        }
        image.setUrl(resolvedUrl);
        image.setAltText(request.altText());
        image.setSortOrder(resolveSortOrder(product, request.sortOrder()));
        image.setMain(makeMain);
        product.getImages().add(image);

        Product saved;
        try {
            saved = productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateObjectKeyViolation(ex)) {
                throw new BusinessException(
                    "PRODUCT_IMAGE_ALREADY_ATTACHED",
                    "Media object is already attached to a product: " + request.objectKey()
                );
            }
            throw ex;
        }
        publishEvent("product.updated", productMapper.toDto(saved));

        return saved.getImages().stream()
            .filter(existing -> Objects.equals(existing.getObjectKey(), request.objectKey()))
            .findFirst()
            .map(productMapper::toImageDto)
            .orElseGet(() -> productMapper.toImageDto(image));
    }

    private void ensureUniqueAttributeNames(List<ProductAttributeRequest> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        java.util.Set<String> normalizedNames = new java.util.HashSet<>();
        for (ProductAttributeRequest attribute : attributes) {
            String normalizedName = attribute.name().trim().toLowerCase(Locale.ROOT);
            if (!normalizedNames.add(normalizedName)) {
                throw new BusinessException(
                    "DUPLICATE_PRODUCT_ATTRIBUTE",
                    "Duplicate product attribute name: " + attribute.name()
                );
            }
        }
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
