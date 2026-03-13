package com.onlinestore.realtime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.entity.ProductStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductRealtimeListenerTest {

    @Mock
    private RealtimeMessageBroadcaster realtimeMessageBroadcaster;

    private ProductRealtimeListener listener;

    @BeforeEach
    void setUp() {
        listener = new ProductRealtimeListener(realtimeMessageBroadcaster);
    }

    @Test
    void handleProductUpdatedShouldBroadcastToRealtimeTopic() {
        ProductDTO product = productDto();

        listener.handleProductEvent(product, "product.updated");

        verify(realtimeMessageBroadcaster).broadcastProductUpdate(product, "product.updated");
    }

    @Test
    void handleProductCreatedShouldBroadcastToRealtimeTopic() {
        ProductDTO product = productDto();

        listener.handleProductEvent(product, "product.created");

        verify(realtimeMessageBroadcaster).broadcastProductUpdate(product, "product.created");
    }

    @Test
    void handleUnknownProductRoutingKeyShouldIgnoreEvent() {
        listener.handleProductEvent(productDto(), "product.deleted");

        verifyNoInteractions(realtimeMessageBroadcaster);
    }

    private ProductDTO productDto() {
        return new ProductDTO(
            10L,
            "Phone",
            "phone",
            "Flagship phone",
            4L,
            "Electronics",
            "electronics",
            ProductStatus.ACTIVE,
            false,
            List.of(),
            List.of(),
            List.of()
        );
    }
}
