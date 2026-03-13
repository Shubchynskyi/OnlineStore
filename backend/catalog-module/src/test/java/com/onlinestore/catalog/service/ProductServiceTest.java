package com.onlinestore.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.catalog.dto.AttachImageRequest;
import com.onlinestore.catalog.dto.CreateProductRequest;
import com.onlinestore.catalog.dto.CreateVariantRequest;
import com.onlinestore.catalog.dto.ImageDTO;
import com.onlinestore.catalog.dto.ProductAttributeRequest;
import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductImage;
import com.onlinestore.catalog.entity.ProductStatus;
import com.onlinestore.catalog.mapper.ProductMapper;
import com.onlinestore.catalog.repository.CategoryRepository;
import com.onlinestore.catalog.repository.ProductRepository;
import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.common.event.OutboxService;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.util.SlugGenerator;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private MediaStorageService mediaStorageService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
            productRepository,
            categoryRepository,
            new ProductMapper(),
            outboxService,
            new SlugGenerator(),
            mediaStorageService,
            transactionTemplate
        );
    }

    @Test
    void attachImageShouldStoreImageAndQueueProductUpdateEvent() {
        var product = new Product();
        product.setId(10L);
        product.setName("Phone");
        product.setSlug("phone");
        product.setStatus(ProductStatus.ACTIVE);

        var existingImage = new ProductImage();
        existingImage.setId(1L);
        existingImage.setProduct(product);
        existingImage.setObjectKey("products/2026/03/current-phone.png");
        existingImage.setUrl("http://localhost:9000/product-images/products/2026/03/current-phone.png");
        existingImage.setSortOrder(0);
        existingImage.setMain(true);

        product.setImages(new LinkedHashSet<>(java.util.List.of(existingImage)));

        when(productRepository.existsById(10L)).thenReturn(true);
        when(productRepository.existsImageByObjectKey("products/2026/03/new-phone.png")).thenReturn(false);
        when(productRepository.findWithDetailsById(10L)).thenReturn(Optional.of(product));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.getImages().stream()
                .filter(image -> image.getId() == null)
                .forEach(image -> image.setId(2L));
            return saved;
        });
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<ImageDTO> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(mediaStorageService.resolveObjectUrl("products/2026/03/new-phone.png"))
            .thenReturn("http://localhost:9000/product-images/products/2026/03/new-phone.png");

        var result = productService.attachImage(
            10L,
            new AttachImageRequest("products/2026/03/new-phone.png", "Updated phone", null, true)
        );

        assertEquals(2L, result.id());
        assertEquals(1, result.sortOrder());
        assertTrue(result.isMain());
        assertFalse(existingImage.isMain());
        verify(mediaStorageService).assertObjectIsAttachable("products/2026/03/new-phone.png");
        verify(productRepository).saveAndFlush(product);

        var payloadCaptor = ArgumentCaptor.forClass(ProductDTO.class);
        verify(outboxService).queueEvent(
            eq(RabbitMQConfig.PRODUCT_EXCHANGE),
            eq("product.updated"),
            payloadCaptor.capture()
        );
        assertEquals(2, payloadCaptor.getValue().images().size());
    }

    @Test
    void attachImageShouldRejectDuplicateObjectKey() {
        var product = new Product();
        product.setId(10L);

        var existingImage = new ProductImage();
        existingImage.setProduct(product);
        existingImage.setObjectKey("products/2026/03/phone.png");
        product.setImages(new LinkedHashSet<>(java.util.List.of(existingImage)));

        when(productRepository.existsById(10L)).thenReturn(true);
        when(productRepository.existsImageByObjectKey("products/2026/03/phone.png")).thenReturn(true);

        assertThrows(
            BusinessException.class,
            () -> productService.attachImage(
                10L,
                new AttachImageRequest("products/2026/03/phone.png", "Duplicate", null, false)
            )
        );

        verify(mediaStorageService, never()).assertObjectIsAttachable(any());
        verify(productRepository, never()).saveAndFlush(any(Product.class));
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void attachImageShouldRejectObjectKeyAlreadyUsedByAnotherProduct() {
        var product = new Product();
        product.setId(10L);

        when(productRepository.existsById(10L)).thenReturn(true);
        when(productRepository.existsImageByObjectKey("products/2026/03/phone.png")).thenReturn(true);

        assertThrows(
            BusinessException.class,
            () -> productService.attachImage(
                10L,
                new AttachImageRequest("products/2026/03/phone.png", "Duplicate", null, false)
            )
        );

        verify(mediaStorageService, never()).assertObjectIsAttachable(any());
        verify(productRepository, never()).saveAndFlush(any(Product.class));
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void createShouldRejectDuplicateAttributeNamesIgnoringCase() {
        var request = new CreateProductRequest(
            "Phone",
            "Flagship phone",
            null,
            false,
            java.util.List.of(
                new CreateVariantRequest("SKU-1", "Default", new BigDecimal("199.99"), "EUR", 5, Map.of())
            ),
            java.util.List.of(
                new ProductAttributeRequest("Brand", Map.of("value", "Acme")),
                new ProductAttributeRequest("brand", Map.of("value", "Other"))
            )
        );

        assertThrows(BusinessException.class, () -> productService.create(request));

        verify(productRepository, never()).save(any(Product.class));
    }
}
