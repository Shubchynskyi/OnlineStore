package com.onlinestore.catalog.repository;

import com.onlinestore.catalog.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @EntityGraph(attributePaths = {"children"})
    List<Category> findByParentIsNullAndActiveTrueOrderBySortOrderAsc();

    Optional<Category> findBySlugAndActiveTrue(String slug);
}
