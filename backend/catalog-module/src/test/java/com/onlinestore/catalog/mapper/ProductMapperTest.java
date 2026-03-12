package com.onlinestore.catalog.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.onlinestore.catalog.entity.Category;
import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductImage;
import com.onlinestore.catalog.entity.ProductStatus;
import com.onlinestore.catalog.entity.ProductVariant;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductMapperTest {

    private final ProductMapper productMapper = new ProductMapper();

    @Test
    void toDtoShouldExposeCanonicalCategorySlug() {
        var category = new Category();
        category.setId(2L);
        category.setName("Smart Phones");
        category.setSlug("mobile-devices");

        var product = new Product();
        product.setId(10L);
        product.setName("Phone");
        product.setDescription("Flagship phone");
        product.setCategory(category);
        product.setStatus(ProductStatus.ACTIVE);

        var lowerPriceVariant = new ProductVariant();
        lowerPriceVariant.setProduct(product);
        lowerPriceVariant.setPriceAmount(new BigDecimal("199.99"));
        lowerPriceVariant.setStock(0);

        var higherPriceVariant = new ProductVariant();
        higherPriceVariant.setProduct(product);
        higherPriceVariant.setPriceAmount(new BigDecimal("249.99"));
        higherPriceVariant.setStock(5);

        var image = new ProductImage();
        image.setProduct(product);
        image.setUrl("https://cdn.example.test/phone.png");

        product.setVariants(List.of(lowerPriceVariant, higherPriceVariant));
        product.setImages(List.of(image));

        var payload = productMapper.toDto(product);

        assertEquals("Smart Phones", payload.categoryName());
        assertEquals("mobile-devices", payload.categorySlug());
    }
}
