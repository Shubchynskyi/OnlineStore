package com.onlinestore.orders.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.port.catalog.ProductVariantGateway;
import com.onlinestore.common.port.catalog.ProductVariantOrderView;
import com.onlinestore.orders.dto.AddCartItemRequest;
import com.onlinestore.orders.dto.UpdateCartItemQuantityRequest;
import com.onlinestore.orders.entity.Cart;
import com.onlinestore.orders.entity.CartItem;
import com.onlinestore.orders.mapper.CartMapper;
import com.onlinestore.orders.repository.CartRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductVariantGateway productVariantGateway;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, productVariantGateway, new CartMapper());
    }

    @Test
    void getCartShouldReturnEmptyCartWhenUserHasNoStoredCart() {
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.empty());

        var result = cartService.getCart(7L);

        assertEquals(new BigDecimal("0"), result.totalAmount());
        assertEquals("EUR", result.totalCurrency());
        assertEquals(0, result.items().size());
    }

    @Test
    void addItemShouldCreateCartAndSnapshotVariantDetails() {
        mockSaveAsPersistedAggregate();
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(productVariantGateway.findById(1000L)).thenReturn(Optional.of(variant(1000L, "SKU-1000", "Blue", "Phone", "10.00", "EUR", 5)));

        var result = cartService.addItem(7L, new AddCartItemRequest(1000L, 2));

        assertEquals(new BigDecimal("20.00"), result.totalAmount());
        assertEquals("EUR", result.totalCurrency());
        assertEquals(1, result.items().size());
        assertEquals(1L, result.items().get(0).id());
        assertEquals(1000L, result.items().get(0).productVariantId());
        assertEquals("Phone", result.items().get(0).productName());
        assertEquals("Blue", result.items().get(0).variantName());
        assertEquals("SKU-1000", result.items().get(0).sku());
        assertEquals(2, result.items().get(0).quantity());
    }

    @Test
    void addItemShouldMergeQuantityForExistingVariant() {
        mockSaveAsPersistedAggregate();
        var existingCart = cartWithItem(7L, 10L, 1000L, 1, "10.00", "EUR");
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(existingCart));
        when(productVariantGateway.findById(1000L)).thenReturn(Optional.of(variant(1000L, "SKU-1000", "Blue", "Phone", "10.00", "EUR", 5)));

        var result = cartService.addItem(7L, new AddCartItemRequest(1000L, 2));

        assertEquals(1, result.items().size());
        assertEquals(3, result.items().get(0).quantity());
        assertEquals(new BigDecimal("30.00"), result.items().get(0).totalAmount());
        assertEquals(new BigDecimal("30.00"), result.totalAmount());
    }

    @Test
    void updateItemQuantityShouldRejectQuantityAboveAvailableStock() {
        var existingCart = cartWithItem(7L, 10L, 1000L, 1, "10.00", "EUR");
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(existingCart));
        when(productVariantGateway.findById(1000L)).thenReturn(Optional.of(variant(1000L, "SKU-1000", "Blue", "Phone", "10.00", "EUR", 2)));

        var exception = assertThrows(
            BusinessException.class,
            () -> cartService.updateItemQuantity(7L, 10L, new UpdateCartItemQuantityRequest(3))
        );

        assertEquals("INSUFFICIENT_STOCK", exception.getErrorCode());
        assertEquals("Insufficient stock for SKU: SKU-1000", exception.getMessage());
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void removeItemShouldReturnEmptyCartAfterRemovingLastLine() {
        mockSaveAsPersistedAggregate();
        var existingCart = cartWithItem(7L, 10L, 1000L, 1, "10.00", "EUR");
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(existingCart));

        var result = cartService.removeItem(7L, 10L);

        assertEquals(0, result.items().size());
        assertEquals(new BigDecimal("0"), result.totalAmount());
        assertEquals("EUR", result.totalCurrency());
    }

    @Test
    void clearCartShouldResetItemsAndTotals() {
        mockSaveAsPersistedAggregate();
        var cart = cartWithItem(7L, 10L, 1000L, 2, "10.00", "EUR");
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(cart));

        var result = cartService.clearCart(7L);

        assertEquals(0, result.items().size());
        assertEquals(new BigDecimal("0"), result.totalAmount());
        assertEquals("EUR", result.totalCurrency());
        verify(cartRepository).save(cart);
    }

    private void mockSaveAsPersistedAggregate() {
        AtomicLong cartSequence = new AtomicLong(100L);
        AtomicLong itemSequence = new AtomicLong(1L);
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            var cart = invocation.getArgument(0, Cart.class);
            if (cart.getId() == null) {
                cart.setId(cartSequence.getAndIncrement());
            }
            for (CartItem item : cart.getItems()) {
                if (item.getId() == null) {
                    item.setId(itemSequence.getAndIncrement());
                }
            }
            return cart;
        });
    }

    private Cart cartWithItem(Long userId, Long itemId, Long variantId, int quantity, String priceAmount, String currency) {
        var cart = new Cart();
        cart.setId(50L);
        cart.setUserId(userId);

        var item = new CartItem();
        item.setId(itemId);
        item.setCart(cart);
        item.setProductVariantId(variantId);
        item.setProductName("Phone");
        item.setVariantName("Blue");
        item.setSku("SKU-1000");
        item.setQuantity(quantity);
        item.setUnitPriceAmount(new BigDecimal(priceAmount));
        item.setUnitPriceCurrency(currency);
        item.setTotalAmount(new BigDecimal(priceAmount).multiply(BigDecimal.valueOf(quantity)));
        cart.getItems().add(item);
        cart.setTotalAmount(item.getTotalAmount());
        cart.setTotalCurrency(currency);
        return cart;
    }

    private ProductVariantOrderView variant(
        Long id,
        String sku,
        String variantName,
        String productName,
        String priceAmount,
        String currency,
        int stock
    ) {
        return new ProductVariantOrderView(
            id,
            sku,
            variantName,
            productName,
            new BigDecimal(priceAmount),
            currency,
            stock
        );
    }
}
