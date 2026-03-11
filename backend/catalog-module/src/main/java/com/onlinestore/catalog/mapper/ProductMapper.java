package com.onlinestore.catalog.mapper;

import com.onlinestore.catalog.dto.CategoryDTO;
import com.onlinestore.catalog.dto.CreateProductRequest;
import com.onlinestore.catalog.dto.CreateVariantRequest;
import com.onlinestore.catalog.dto.ImageDTO;
import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.dto.UpdateProductRequest;
import com.onlinestore.catalog.dto.VariantDTO;
import com.onlinestore.catalog.entity.Category;
import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductVariant;
import java.util.ArrayList;
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
            product.getStatus(),
            product.isFeatured(),
            product.getVariants().stream().map(this::toVariantDto).toList(),
            product.getImages().stream().map(image ->
                new ImageDTO(
                    image.getId(),
                    image.getUrl(),
                    image.getAltText(),
                    image.getSortOrder(),
                    image.isMain()
                )
            ).toList()
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
        product.setVariants(new ArrayList<>());
        for (CreateVariantRequest variantRequest : request.variants()) {
            product.getVariants().add(toVariantEntity(product, variantRequest));
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

    public List<CategoryDTO> toCategoryDtos(List<Category> categories) {
        return categories.stream().map(this::toCategoryDto).toList();
    }
}
