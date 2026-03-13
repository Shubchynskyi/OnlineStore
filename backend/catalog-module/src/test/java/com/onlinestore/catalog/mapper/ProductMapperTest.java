package com.onlinestore.catalog.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.onlinestore.catalog.entity.Category;
import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductAttribute;
import com.onlinestore.catalog.entity.ProductImage;
import com.onlinestore.catalog.entity.ProductStatus;
import com.onlinestore.catalog.entity.ProductVariant;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        lowerPriceVariant.setId(11L);
        lowerPriceVariant.setProduct(product);
        lowerPriceVariant.setPriceAmount(new BigDecimal("199.99"));
        lowerPriceVariant.setStock(0);
        lowerPriceVariant.setAttributes(Map.of("color", "black"));

        var higherPriceVariant = new ProductVariant();
        higherPriceVariant.setId(12L);
        higherPriceVariant.setProduct(product);
        higherPriceVariant.setPriceAmount(new BigDecimal("249.99"));
        higherPriceVariant.setStock(5);

        var image = new ProductImage();
        image.setId(31L);
        image.setProduct(product);
        image.setUrl("https://cdn.example.test/phone.png");
        image.setSortOrder(1);

        var attribute = new ProductAttribute();
        attribute.setId(21L);
        attribute.setProduct(product);
        attribute.setName("brand");
        attribute.setValue(Map.of("value", "Acme"));

        product.setVariants(new LinkedHashSet<>(List.of(lowerPriceVariant, higherPriceVariant)));
        product.setImages(new LinkedHashSet<>(List.of(image)));
        product.setAttributes(new LinkedHashSet<>(List.of(attribute)));

        var payload = productMapper.toDto(product);

        assertEquals("Smart Phones", payload.categoryName());
        assertEquals("mobile-devices", payload.categorySlug());
        assertEquals("brand", payload.attributes().get(0).name());
        assertEquals("Acme", payload.attributes().get(0).value().get("value"));
        assertThrows(UnsupportedOperationException.class, () -> payload.attributes().get(0).value().put("other", "x"));
        assertThrows(UnsupportedOperationException.class, () -> payload.variants().get(0).attributes().put("size", "L"));
    }
}
