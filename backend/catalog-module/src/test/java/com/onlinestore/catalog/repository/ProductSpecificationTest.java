package com.onlinestore.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class ProductSpecificationTest {

    @Mock
    private Root<Product> root;

    @Mock
    private CriteriaQuery<Product> query;

    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Mock
    private Predicate predicate;

    @Mock
    private Predicate minPredicate;

    @Mock
    private Predicate maxPredicate;

    @Mock
    private Path statusPath;

    @Mock
    private Path categoryPath;

    @Mock
    private Path categoryIdPath;

    @Mock
    private Path namePath;

    @Mock
    private Join variantJoin;

    @Mock
    private Path activePath;

    @Mock
    private Path pricePath;

    @Test
    void hasStatusShouldCompareProductStatus() {
        when(root.get("status")).thenReturn(statusPath);
        when(criteriaBuilder.equal(statusPath, ProductStatus.ACTIVE)).thenReturn(predicate);

        var result = ProductSpecification.hasStatus(ProductStatus.ACTIVE).toPredicate(root, query, criteriaBuilder);

        assertThat(result).isSameAs(predicate);
        verify(criteriaBuilder).equal(statusPath, ProductStatus.ACTIVE);
    }

    @Test
    void inCategoryShouldCompareCategoryId() {
        when(root.get("category")).thenReturn(categoryPath);
        when(categoryPath.get("id")).thenReturn(categoryIdPath);
        when(criteriaBuilder.equal(categoryIdPath, 10L)).thenReturn(predicate);

        var result = ProductSpecification.inCategory(10L).toPredicate(root, query, criteriaBuilder);

        assertThat(result).isSameAs(predicate);
        verify(criteriaBuilder).equal(categoryIdPath, 10L);
    }

    @Test
    void nameContainsShouldApplyLowercaseLikeSearch() {
        when(root.get("name")).thenReturn(namePath);
        when(criteriaBuilder.lower(namePath)).thenReturn(namePath);
        when(criteriaBuilder.like(namePath, "%phone%")).thenReturn(predicate);

        var result = ProductSpecification.nameContains("Phone").toPredicate(root, query, criteriaBuilder);

        assertThat(result).isSameAs(predicate);
        verify(criteriaBuilder).like(namePath, "%phone%");
    }

    @Test
    void priceRangeShouldAddActiveAndBoundaryPredicates() {
        when(root.join("variants")).thenReturn(variantJoin);
        when(variantJoin.get("active")).thenReturn(activePath);
        when(variantJoin.get("priceAmount")).thenReturn(pricePath);
        when(criteriaBuilder.isTrue(activePath)).thenReturn(predicate);
        when(criteriaBuilder.ge(pricePath, new BigDecimal("10.00"))).thenReturn(minPredicate);
        when(criteriaBuilder.le(pricePath, new BigDecimal("20.00"))).thenReturn(maxPredicate);
        when(criteriaBuilder.and(predicate, minPredicate, maxPredicate)).thenReturn(maxPredicate);

        var result = ProductSpecification.priceRange(new BigDecimal("10.00"), new BigDecimal("20.00"))
            .toPredicate(root, query, criteriaBuilder);

        assertThat(result).isSameAs(maxPredicate);
        verify(query).distinct(true);
        verify(criteriaBuilder).isTrue(activePath);
        verify(criteriaBuilder).ge(pricePath, new BigDecimal("10.00"));
        verify(criteriaBuilder).le(pricePath, new BigDecimal("20.00"));
    }
}
