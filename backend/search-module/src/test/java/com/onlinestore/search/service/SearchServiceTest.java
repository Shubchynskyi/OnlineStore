package com.onlinestore.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.onlinestore.catalog.dto.ImageDTO;
import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.dto.VariantDTO;
import com.onlinestore.catalog.entity.ProductStatus;
import com.onlinestore.catalog.service.CategoryService;
import com.onlinestore.search.document.ProductDocument;
import com.onlinestore.search.repository.ProductDocumentRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;
    @Mock
    private ProductDocumentRepository productDocumentRepository;
    @Mock
    private CategoryService categoryService;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(elasticsearchOperations, productDocumentRepository, categoryService);
    }

    @Test
    void indexProductShouldUseCanonicalCategorySlugFromDto() {
        var payload = new ProductDTO(
            10L,
            "Phone",
            "phone",
            "Flagship phone",
            2L,
            "Smart Phones",
            "mobile-devices",
            ProductStatus.ACTIVE,
            false,
            List.of(new VariantDTO(1L, "SKU-1", "Default", new BigDecimal("199.99"), "EUR", null, 5, java.util.Map.of(), true)),
            List.of(new ImageDTO(1L, "https://cdn.example.test/phone.png", "Phone", 0, true)),
            List.of()
        );

        searchService.indexProduct(payload);

        var documentCaptor = ArgumentCaptor.forClass(ProductDocument.class);
        verify(productDocumentRepository).save(documentCaptor.capture());
        assertEquals("mobile-devices", documentCaptor.getValue().getCategorySlug());
        assertEquals("Smart Phones", documentCaptor.getValue().getCategoryName());
        verifyNoInteractions(categoryService);
    }

    @Test
    void indexProductShouldResolveCanonicalCategorySlugForLegacyMessages() {
        var payload = new ProductDTO(
            10L,
            "Phone",
            "phone",
            "Flagship phone",
            2L,
            "Smart Phones",
            null,
            ProductStatus.ACTIVE,
            false,
            List.of(new VariantDTO(1L, "SKU-1", "Default", new BigDecimal("199.99"), "EUR", null, 5, java.util.Map.of(), true)),
            List.of(new ImageDTO(1L, "https://cdn.example.test/phone.png", "Phone", 0, true)),
            List.of()
        );
        when(categoryService.findSlugById(2L)).thenReturn(java.util.Optional.of("mobile-devices"));

        searchService.indexProduct(payload);

        var documentCaptor = ArgumentCaptor.forClass(ProductDocument.class);
        verify(productDocumentRepository).save(documentCaptor.capture());
        assertEquals("mobile-devices", documentCaptor.getValue().getCategorySlug());
        verify(categoryService).findSlugById(2L);
    }

    @Test
    void indexProductShouldLeaveCategorySlugNullWhenLegacyCategoryCannotBeResolved() {
        var payload = new ProductDTO(
            10L,
            "Phone",
            "phone",
            "Flagship phone",
            2L,
            "Smart Phones",
            null,
            ProductStatus.ACTIVE,
            false,
            List.of(new VariantDTO(1L, "SKU-1", "Default", new BigDecimal("199.99"), "EUR", null, 5, java.util.Map.of(), true)),
            List.of(new ImageDTO(1L, "https://cdn.example.test/phone.png", "Phone", 0, true)),
            List.of()
        );
        when(categoryService.findSlugById(2L)).thenReturn(java.util.Optional.empty());

        searchService.indexProduct(payload);

        var documentCaptor = ArgumentCaptor.forClass(ProductDocument.class);
        verify(productDocumentRepository).save(documentCaptor.capture());
        assertNull(documentCaptor.getValue().getCategorySlug());
        verify(categoryService).findSlugById(2L);
    }

    @Test
    void productDtoDeserializationShouldRemainBackwardCompatibleWhenCategorySlugMissing() throws Exception {
        var json = """
            {
              "id": 10,
              "name": "Phone",
              "slug": "phone",
              "description": "Flagship phone",
              "categoryId": 2,
              "categoryName": "Smart Phones",
              "status": "ACTIVE",
              "isFeatured": false,
              "variants": [],
              "images": []
            }
            """;

        var product = new ObjectMapper().readValue(json, ProductDTO.class);

        assertNull(product.categorySlug());
    }
}
