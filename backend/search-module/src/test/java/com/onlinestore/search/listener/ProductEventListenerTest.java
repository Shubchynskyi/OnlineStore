package com.onlinestore.search.listener;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.entity.ProductStatus;
import com.onlinestore.search.service.SearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductEventListenerTest {

    @Mock
    private SearchService searchService;

    private ProductEventListener listener;
    private ProductDTO product;

    @BeforeEach
    void setUp() {
        listener = new ProductEventListener(searchService);
        product = new ProductDTO(
            15L,
            "Phone",
            "phone",
            "Flagship phone",
            1L,
            "Electronics",
            "electronics",
            ProductStatus.ACTIVE,
            true,
            List.of(),
            List.of(),
            List.of()
        );
    }

    @Test
    void handleProductEventShouldIndexCreatedAndUpdatedProducts() {
        listener.handleProductEvent(product, "product.created");
        listener.handleProductEvent(product, "product.updated");

        verify(searchService, times(2)).indexProduct(product);
    }

    @Test
    void handleProductEventShouldDeleteRemovedProducts() {
        listener.handleProductEvent(product, "product.deleted");

        verify(searchService).deleteProduct(15L);
    }

    @Test
    void handleProductEventShouldIgnoreUnknownRoutingKeys() {
        listener.handleProductEvent(product, "product.unknown");

        verifyNoInteractions(searchService);
    }
}
