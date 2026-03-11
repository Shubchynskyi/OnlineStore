package com.onlinestore.catalog.repository;

import com.onlinestore.catalog.entity.ProductVariant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findBySku(String sku);

    @Query("SELECT pv FROM ProductVariant pv JOIN FETCH pv.product WHERE pv.id IN :ids")
    List<ProductVariant> findAllWithProductByIdIn(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("UPDATE ProductVariant pv SET pv.stock = pv.stock - :quantity WHERE pv.id = :variantId AND pv.stock >= :quantity")
    int reserveStock(@Param("variantId") Long variantId, @Param("quantity") Integer quantity);
}
