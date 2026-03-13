package com.onlinestore.catalog.mapper;

import com.onlinestore.catalog.dto.CategoryDTO;
import com.onlinestore.catalog.dto.CreateProductRequest;
import com.onlinestore.catalog.dto.ImageDTO;
import com.onlinestore.catalog.dto.ProductAttributeDTO;
import com.onlinestore.catalog.dto.ProductAttributeRequest;
import com.onlinestore.catalog.dto.CreateVariantRequest;
import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.dto.UpdateProductRequest;
import com.onlinestore.catalog.dto.VariantDTO;
import com.onlinestore.catalog.entity.Category;
import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductAttribute;
import com.onlinestore.catalog.entity.ProductImage;
import com.onlinestore.catalog.entity.ProductVariant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductDTO toDto(Product product) {
        return new ProductDTO(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getCategory() == null ? null : product.getCategory().getId(),
            product.getCategory() == null ? null : product.getCategory().getName(),
            product.getCategory() == null ? null : product.getCategory().getSlug(),
            product.getStatus(),
            product.isFeatured(),
            product.getVariants().stream()
                .sorted(Comparator.comparing(ProductVariant::getId, Comparator.nullsLast(Long::compareTo))
                    .thenComparing(ProductVariant::getSku, Comparator.nullsLast(String::compareTo)))
                .map(this::toVariantDto)
                .toList(),
            product.getImages().stream()
                .sorted(Comparator.comparing(ProductImage::getSortOrder)
                    .thenComparing(ProductImage::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::toImageDto)
                .toList(),
            product.getAttributes().stream()
                .sorted(Comparator.comparing(ProductAttribute::getId, Comparator.nullsLast(Long::compareTo))
                    .thenComparing(ProductAttribute::getName, Comparator.nullsLast(String::compareTo)))
                .map(this::toAttributeDto)
                .toList()
        );
    }

    public CategoryDTO toCategoryDto(Category category) {
        return new CategoryDTO(category.getId(), category.getName(), category.getSlug(), category.getDescription());
    }

    public Product toEntity(CreateProductRequest request) {
        var product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setFeatured(request.isFeatured());
        product.setVariants(new LinkedHashSet<>());
        product.setAttributes(new LinkedHashSet<>());
        for (CreateVariantRequest variantRequest : request.variants()) {
            product.getVariants().add(toVariantEntity(product, variantRequest));
        }
        if (request.attributes() != null) {
            for (ProductAttributeRequest attributeRequest : request.attributes()) {
                product.getAttributes().add(toAttributeEntity(product, attributeRequest));
            }
        }
        return product;
    }

    public void updateEntity(Product product, UpdateProductRequest request) {
        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        if (request.isFeatured() != null) {
            product.setFeatured(request.isFeatured());
        }
        if (request.attributes() != null) {
            product.getAttributes().clear();
            for (ProductAttributeRequest attributeRequest : request.attributes()) {
                product.getAttributes().add(toAttributeEntity(product, attributeRequest));
            }
        }
    }

    public ImageDTO toImageDto(ProductImage image) {
        return new ImageDTO(
            image.getId(),
            image.getUrl(),
            image.getAltText(),
            image.getSortOrder(),
            image.isMain()
        );
    }

    private VariantDTO toVariantDto(ProductVariant variant) {
        return new VariantDTO(
            variant.getId(),
            variant.getSku(),
            variant.getName(),
            variant.getPriceAmount(),
            variant.getPriceCurrency(),
            variant.getCompareAtPrice(),
            variant.getStock(),
            variant.getAttributes(),
            variant.isActive()
        );
    }

    private ProductAttributeDTO toAttributeDto(ProductAttribute attribute) {
        return new ProductAttributeDTO(attribute.getId(), attribute.getName(), attribute.getValue());
    }

    private ProductVariant toVariantEntity(Product product, CreateVariantRequest request) {
        var variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(request.sku());
        variant.setName(request.name());
        variant.setPriceAmount(request.price());
        variant.setPriceCurrency(request.currency() == null ? "EUR" : request.currency());
        variant.setStock(request.stock());
        variant.setAttributes(request.attributes() == null ? new java.util.HashMap<>() : request.attributes());
        return variant;
    }

    private ProductAttribute toAttributeEntity(Product product, ProductAttributeRequest request) {
        var attribute = new ProductAttribute();
        attribute.setProduct(product);
        attribute.setName(request.name());
        attribute.setValue(request.value() == null ? new HashMap<>() : new HashMap<>(request.value()));
        return attribute;
    }

    public List<CategoryDTO> toCategoryDtos(List<Category> categories) {
        return categories.stream().map(this::toCategoryDto).toList();
    }
}
