package com.onlinestore.catalog.repository;

import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Product> inCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> nameContains(String name) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Product> priceRange(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            var variantJoin = root.join("variants");
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isTrue(variantJoin.get("active")));
            if (min != null) {
                predicates.add(cb.ge(variantJoin.get("priceAmount"), min));
            }
            if (max != null) {
                predicates.add(cb.le(variantJoin.get("priceAmount"), max));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
