package com.onlinestore.orders.service;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.common.port.catalog.ProductVariantGateway;
import com.onlinestore.common.port.catalog.ProductVariantOrderView;
import com.onlinestore.orders.dto.AddCartItemRequest;
import com.onlinestore.orders.dto.CartDTO;
import com.onlinestore.orders.dto.UpdateCartItemQuantityRequest;
import com.onlinestore.orders.entity.Cart;
import com.onlinestore.orders.entity.CartItem;
import com.onlinestore.orders.mapper.CartMapper;
import com.onlinestore.orders.repository.CartRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductVariantGateway productVariantGateway;
    private final CartMapper cartMapper;

    @Transactional(readOnly = true)
    public CartDTO getCart(Long userId) {
        return cartRepository.findByUserId(userId)
            .map(cartMapper::toDto)
            .orElseGet(cartMapper::empty);
    }

    @Transactional
    public CartDTO addItem(Long userId, AddCartItemRequest request) {
        var cart = findOrCreateCart(userId);
        var variant = findVariant(request.productVariantId());
        var cartItem = findItemByVariantId(cart, request.productVariantId())
            .orElseGet(() -> createCartItem(cart, request.productVariantId()));

        int requestedQuantity = cartItem.getQuantity() == null
            ? request.quantity()
            : cartItem.getQuantity() + request.quantity();
        applyVariantSnapshot(cart, cartItem, variant, requestedQuantity);

        var savedCart = cartRepository.save(cart);
        var savedItem = findItemByVariantId(savedCart, variant.id()).orElse(cartItem);
        log.info("Cart item added: userId={}, itemId={}, variantId={}, quantity={}, totalAmount={}",
            userId,
            savedItem.getId(),
            variant.id(),
            savedItem.getQuantity(),
            savedCart.getTotalAmount());
        return cartMapper.toDto(savedCart);
    }

    @Transactional
    public CartDTO updateItemQuantity(Long userId, Long itemId, UpdateCartItemQuantityRequest request) {
        var cart = findCartForItem(userId, itemId);
        var cartItem = findItemById(cart, itemId);
        var variant = findVariant(cartItem.getProductVariantId());

        applyVariantSnapshot(cart, cartItem, variant, request.quantity());

        var savedCart = cartRepository.save(cart);
        log.info("Cart item quantity updated: userId={}, itemId={}, variantId={}, quantity={}, totalAmount={}",
            userId,
            itemId,
            cartItem.getProductVariantId(),
            request.quantity(),
            savedCart.getTotalAmount());
        return cartMapper.toDto(savedCart);
    }

    @Transactional
    public CartDTO removeItem(Long userId, Long itemId) {
        var cart = findCartForItem(userId, itemId);
        boolean removed = cart.getItems().removeIf(item -> itemId.equals(item.getId()));
        if (!removed) {
            throw new ResourceNotFoundException("CartItem", "id", itemId);
        }

        recalculateCart(cart);

        var savedCart = cartRepository.save(cart);
        log.info("Cart item removed: userId={}, itemId={}, itemsCount={}, totalAmount={}",
            userId,
            itemId,
            savedCart.getItems().size(),
            savedCart.getTotalAmount());
        return cartMapper.toDto(savedCart);
    }

    @Transactional
    public CartDTO clearCart(Long userId) {
        var cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return cartMapper.empty();
        }

        cart.getItems().clear();
        recalculateCart(cart);

        var savedCart = cartRepository.save(cart);
        log.info("Cart cleared: userId={}, itemsCount={}, totalAmount={}",
            userId,
            savedCart.getItems().size(),
            savedCart.getTotalAmount());
        return cartMapper.toDto(savedCart);
    }

    private Cart findOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
            .orElseGet(() -> {
                var cart = new Cart();
                cart.setUserId(userId);
                return cart;
            });
    }

    private Cart findCartForItem(Long userId, Long itemId) {
        return cartRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));
    }

    private CartItem findItemById(Cart cart, Long itemId) {
        return cart.getItems().stream()
            .filter(item -> itemId.equals(item.getId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));
    }

    private Optional<CartItem> findItemByVariantId(Cart cart, Long productVariantId) {
        return cart.getItems().stream()
            .filter(item -> productVariantId.equals(item.getProductVariantId()))
            .findFirst();
    }

    private CartItem createCartItem(Cart cart, Long productVariantId) {
        var cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProductVariantId(productVariantId);
        cart.getItems().add(cartItem);
        return cartItem;
    }

    private ProductVariantOrderView findVariant(Long productVariantId) {
        return productVariantGateway.findById(productVariantId)
            .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", "id", productVariantId));
    }

    private void applyVariantSnapshot(Cart cart, CartItem cartItem, ProductVariantOrderView variant, int quantity) {
        validateStock(variant, quantity);

        cartItem.setProductVariantId(variant.id());
        cartItem.setProductName(variant.productName());
        cartItem.setVariantName(variant.name());
        cartItem.setSku(variant.sku());
        cartItem.setQuantity(quantity);
        cartItem.setUnitPriceAmount(variant.priceAmount());
        cartItem.setUnitPriceCurrency(variant.priceCurrency());
        cartItem.setTotalAmount(variant.priceAmount().multiply(BigDecimal.valueOf(quantity)));

        recalculateCart(cart);
    }

    private void validateStock(ProductVariantOrderView variant, int quantity) {
        if (variant.stock() < quantity) {
            throw new BusinessException("INSUFFICIENT_STOCK", "Insufficient stock for SKU: " + variant.sku());
        }
    }

    private void recalculateCart(Cart cart) {
        if (cart.getItems().isEmpty()) {
            cart.setTotalAmount(BigDecimal.ZERO);
            cart.setTotalCurrency(Cart.DEFAULT_CURRENCY);
            return;
        }

        String currency = cart.getItems().get(0).getUnitPriceCurrency();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            if (!currency.equals(item.getUnitPriceCurrency())) {
                throw new BusinessException("UNSUPPORTED_CART_CURRENCY", "Cart items must use a single currency");
            }
            totalAmount = totalAmount.add(item.getTotalAmount());
        }

        cart.setTotalCurrency(currency);
        cart.setTotalAmount(totalAmount);
    }
}
