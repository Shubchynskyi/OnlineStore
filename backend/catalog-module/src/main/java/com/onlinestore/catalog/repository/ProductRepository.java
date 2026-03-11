package com.onlinestore.catalog.repository;

import com.onlinestore.catalog.entity.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    @EntityGraph(attributePaths = {"variants", "images", "category"})
    Optional<Product> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"variants", "images", "category"})
    Optional<Product> findWithDetailsBySlug(String slug);

    @EntityGraph(attributePaths = {"variants", "images", "category"})
    @Query("SELECT DISTINCT p FROM Product p WHERE p.id IN :ids")
    List<Product> findAllWithDetailsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("SELECT p FROM Product p WHERE p.status = 'ACTIVE' AND p.category.id = :categoryId")
    Page<Product> findActiveByCategory(@Param("categoryId") Long categoryId, Pageable pageable);

    boolean existsBySlug(String slug);
}
