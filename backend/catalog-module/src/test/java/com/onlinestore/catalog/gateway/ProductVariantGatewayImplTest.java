package com.onlinestore.catalog.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.catalog.event.ProductLowStockEvent;
import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductVariant;
import com.onlinestore.catalog.repository.ProductVariantRepository;
import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.common.event.OutboxService;
import com.onlinestore.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductVariantGatewayImplTest {

    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private OutboxService outboxService;

    private ProductVariantGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new ProductVariantGatewayImpl(productVariantRepository, outboxService);
    }

    @Test
    void findByIdShouldMapOrderView() {
        var product = new Product();
        product.setName("Phone");

        var variant = new ProductVariant();
        variant.setId(101L);
        variant.setSku("SKU-101");
        variant.setName("Black 128GB");
        variant.setProduct(product);
        variant.setPriceAmount(new BigDecimal("199.99"));
        variant.setPriceCurrency("EUR");
        variant.setStock(12);

        when(productVariantRepository.findAllWithProductByIdIn(List.of(101L))).thenReturn(List.of(variant));

        var result = gateway.findById(101L);

        assertTrue(result.isPresent());
        assertEquals(101L, result.get().id());
        assertEquals("SKU-101", result.get().sku());
        assertEquals("Phone", result.get().productName());
        assertEquals(new BigDecimal("199.99"), result.get().priceAmount());
        assertEquals(12, result.get().stock());
    }

    @Test
    void reserveStockShouldPersistReservation() {
        when(productVariantRepository.reserveStock(202L, 2)).thenReturn(1);
        when(productVariantRepository.findAllWithProductByIdIn(List.of(202L))).thenReturn(List.of(variant(
            202L,
            "SKU-202",
            "Silver",
            "Laptop",
            8,
            5
        )));

        gateway.reserveStock(202L, 2);

        verify(productVariantRepository).reserveStock(202L, 2);
        verify(outboxService, never()).queueEvent(any(), any(), any());
    }

    @Test
    void reserveStockShouldPublishLowStockEventWhenThresholdIsCrossed() {
        when(productVariantRepository.reserveStock(202L, 1)).thenReturn(1);
        when(productVariantRepository.findAllWithProductByIdIn(List.of(202L))).thenReturn(List.of(variant(
            202L,
            "SKU-202",
            "Silver",
            "Laptop",
            5,
            5
        )));

        gateway.reserveStock(202L, 1);

        verify(outboxService).queueEvent(
            eq(RabbitMQConfig.PRODUCT_EXCHANGE),
            eq("product.low-stock"),
            any(ProductLowStockEvent.class)
        );
    }

    @Test
    void reserveStockShouldNotRepublishLowStockEventWhenVariantWasAlreadyBelowThreshold() {
        when(productVariantRepository.reserveStock(202L, 1)).thenReturn(1);
        when(productVariantRepository.findAllWithProductByIdIn(List.of(202L))).thenReturn(List.of(variant(
            202L,
            "SKU-202",
            "Silver",
            "Laptop",
            4,
            5
        )));

        gateway.reserveStock(202L, 1);

        verify(outboxService, never()).queueEvent(any(), any(), any());
    }

    @Test
    void reserveStockShouldThrowWhenVariantCannotBeReserved() {
        when(productVariantRepository.reserveStock(202L, 2)).thenReturn(0);

        assertThrows(BusinessException.class, () -> gateway.reserveStock(202L, 2));
    }

    private ProductVariant variant(
        Long id,
        String sku,
        String variantName,
        String productName,
        Integer stock,
        Integer lowStockThreshold
    ) {
        var product = new Product();
        product.setId(301L);
        product.setName(productName);

        var variant = new ProductVariant();
        variant.setId(id);
        variant.setSku(sku);
        variant.setName(variantName);
        variant.setProduct(product);
        variant.setPriceAmount(new BigDecimal("199.99"));
        variant.setPriceCurrency("EUR");
        variant.setStock(stock);
        variant.setLowStockThreshold(lowStockThreshold);
        return variant;
    }
}
