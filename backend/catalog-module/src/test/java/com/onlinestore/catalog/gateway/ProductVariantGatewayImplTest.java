package com.onlinestore.catalog.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductVariant;
import com.onlinestore.catalog.repository.ProductVariantRepository;
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

    private ProductVariantGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new ProductVariantGatewayImpl(productVariantRepository);
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

        gateway.reserveStock(202L, 2);

        verify(productVariantRepository).reserveStock(202L, 2);
    }

    @Test
    void reserveStockShouldThrowWhenVariantCannotBeReserved() {
        when(productVariantRepository.reserveStock(202L, 2)).thenReturn(0);

        assertThrows(BusinessException.class, () -> gateway.reserveStock(202L, 2));
    }
}
