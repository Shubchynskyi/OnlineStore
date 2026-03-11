package com.onlinestore.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.catalog.dto.CategoryDTO;
import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.entity.Category;
import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductStatus;
import com.onlinestore.catalog.mapper.ProductMapper;
import com.onlinestore.catalog.repository.CategoryRepository;
import com.onlinestore.catalog.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductMapper productMapper;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, productRepository, productMapper);
    }

    @Test
    void getBySlugWithProductsShouldMapDetailsFromSecondStepLoad() {
        var pageable = PageRequest.of(0, 20);
        var category = new Category();
        category.setId(2L);

        var summaryProduct = new Product();
        summaryProduct.setId(10L);

        var detailedProduct = new Product();
        detailedProduct.setId(10L);

        var categoryDto = new CategoryDTO(2L, "Electronics", "electronics", "Category description");
        var productDto = new ProductDTO(
            10L,
            "Phone",
            "phone",
            "Description",
            2L,
            "Electronics",
            ProductStatus.ACTIVE,
            false,
            List.of(),
            List.of()
        );

        when(categoryRepository.findBySlugAndActiveTrue("electronics")).thenReturn(Optional.of(category));
        when(productRepository.findActiveByCategory(2L, pageable))
            .thenReturn(new PageImpl<>(List.of(summaryProduct), pageable, 1));
        when(productRepository.findAllWithDetailsByIdIn(List.of(10L))).thenReturn(List.of(detailedProduct));
        when(productMapper.toCategoryDto(category)).thenReturn(categoryDto);
        when(productMapper.toDto(detailedProduct)).thenReturn(productDto);

        var result = categoryService.getBySlugWithProducts("electronics", pageable);

        assertEquals(categoryDto, result.category());
        assertEquals(1, result.products().content().size());
        assertEquals(productDto, result.products().content().get(0));
        verify(productMapper, never()).toDto(summaryProduct);
    }
}
