package com.onlinestore.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.catalog.dto.AttachImageRequest;
import com.onlinestore.catalog.dto.CategoryDTO;
import com.onlinestore.catalog.dto.CategoryWithProductsDTO;
import com.onlinestore.catalog.dto.CreateProductRequest;
import com.onlinestore.catalog.dto.GenerateUploadUrlRequest;
import com.onlinestore.catalog.dto.ImageDTO;
import com.onlinestore.catalog.dto.MediaUploadResponse;
import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.dto.ProductFilterRequest;
import com.onlinestore.catalog.dto.UpdateProductRequest;
import com.onlinestore.catalog.service.CategoryService;
import com.onlinestore.catalog.service.MediaStorageService;
import com.onlinestore.catalog.service.ProductService;
import com.onlinestore.common.dto.PageResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CatalogApiControllersTest {

    @Mock
    private ProductService productService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private MediaStorageService mediaStorageService;

    private CatalogController catalogController;
    private AdminMediaController adminMediaController;

    @BeforeEach
    void setUp() {
        catalogController = new CatalogController(productService, categoryService);
        adminMediaController = new AdminMediaController(mediaStorageService, productService);
    }

    @Test
    void getCategoriesShouldDelegateToCategoryService() {
        var categories = List.of(mock(CategoryDTO.class));
        when(categoryService.getCategoryTree()).thenReturn(categories);

        assertThat(catalogController.getCategories()).isSameAs(categories);

        verify(categoryService).getCategoryTree();
    }

    @Test
    void getCategoryBySlugShouldDelegateToCategoryService() {
        var pageable = Pageable.unpaged();
        var response = mock(CategoryWithProductsDTO.class);
        when(categoryService.getBySlugWithProducts("electronics", pageable)).thenReturn(response);

        assertThat(catalogController.getCategoryBySlug("electronics", pageable)).isSameAs(response);

        verify(categoryService).getBySlugWithProducts("electronics", pageable);
    }

    @Test
    void getProductsShouldDelegateToProductService() {
        var filter = mock(ProductFilterRequest.class);
        var pageable = Pageable.unpaged();
        var response = new PageResponse<ProductDTO>(List.of(), 0, 20, 0, 0, true);
        when(productService.findAll(filter, pageable)).thenReturn(response);

        assertThat(catalogController.getProducts(filter, pageable)).isSameAs(response);

        verify(productService).findAll(filter, pageable);
    }

    @Test
    void getProductBySlugShouldDelegateToProductService() {
        var product = mock(ProductDTO.class);
        when(productService.findBySlug("phone")).thenReturn(product);

        assertThat(catalogController.getProductBySlug("phone")).isSameAs(product);

        verify(productService).findBySlug("phone");
    }

    @Test
    void createProductShouldDelegateToProductService() {
        var request = mock(CreateProductRequest.class);
        var product = mock(ProductDTO.class);
        when(productService.create(request)).thenReturn(product);

        assertThat(catalogController.createProduct(request)).isSameAs(product);

        verify(productService).create(request);
    }

    @Test
    void updateProductShouldDelegateToProductService() {
        var request = mock(UpdateProductRequest.class);
        var product = mock(ProductDTO.class);
        when(productService.update(11L, request)).thenReturn(product);

        assertThat(catalogController.updateProduct(11L, request)).isSameAs(product);

        verify(productService).update(11L, request);
    }

    @Test
    void createUploadUrlShouldDelegateToMediaStorageService() {
        var request = mock(GenerateUploadUrlRequest.class);
        var response = mock(MediaUploadResponse.class);
        when(mediaStorageService.generateUploadUrl(request)).thenReturn(response);

        assertThat(adminMediaController.createUploadUrl(request)).isSameAs(response);

        verify(mediaStorageService).generateUploadUrl(request);
    }

    @Test
    void attachImageShouldDelegateToProductService() {
        var request = mock(AttachImageRequest.class);
        var image = mock(ImageDTO.class);
        when(productService.attachImage(15L, request)).thenReturn(image);

        assertThat(adminMediaController.attachImage(15L, request)).isSameAs(image);

        verify(productService).attachImage(15L, request);
    }
}
