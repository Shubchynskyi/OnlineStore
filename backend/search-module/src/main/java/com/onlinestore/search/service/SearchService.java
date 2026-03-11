package com.onlinestore.search.service;

import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.catalog.dto.VariantDTO;
import com.onlinestore.search.document.ProductDocument;
import com.onlinestore.search.dto.ProductSearchRequest;
import com.onlinestore.search.dto.ProductSearchResult;
import com.onlinestore.search.repository.ProductDocumentRepository;
import com.onlinestore.common.dto.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "onlinestore.search.enabled", havingValue = "true", matchIfMissing = true)
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductDocumentRepository productDocumentRepository;

    public PageResponse<ProductSearchResult> search(ProductSearchRequest request, Pageable pageable) {
        var criteria = new Criteria("status").is("ACTIVE");
        if (request.query() != null && !request.query().isBlank()) {
            criteria = criteria.and(new Criteria("name").matches(request.query())
                .or(new Criteria("description").matches(request.query())));
        }
        if (request.category() != null && !request.category().isBlank()) {
            criteria = criteria.and(new Criteria("categorySlug").is(request.category()));
        }
        if (request.priceMin() != null) {
            criteria = criteria.and(new Criteria("minPrice").greaterThanEqual(request.priceMin()));
        }
        if (request.priceMax() != null) {
            criteria = criteria.and(new Criteria("maxPrice").lessThanEqual(request.priceMax()));
        }

        var query = new CriteriaQuery(criteria);
        query.setPageable(pageable);

        var hits = elasticsearchOperations.search(query, ProductDocument.class);
        var results = hits.getSearchHits().stream()
            .map(this::mapToResult)
            .toList();

        long totalHits = hits.getTotalHits();
        int totalPages = pageable.getPageSize() == 0
            ? 1
            : (int) Math.ceil((double) totalHits / pageable.getPageSize());
        return new PageResponse<>(
            results,
            pageable.getPageNumber(),
            pageable.getPageSize(),
            totalHits,
            totalPages,
            pageable.getOffset() + results.size() >= totalHits
        );
    }

    public List<String> suggest(String query) {
        var criteria = new CriteriaQuery(new Criteria("suggest").startsWith(query));
        criteria.setPageable(PageRequest.of(0, 10));
        return elasticsearchOperations.search(criteria, ProductDocument.class)
            .getSearchHits().stream()
            .map(hit -> hit.getContent().getName())
            .distinct()
            .toList();
    }

    public void indexProduct(ProductDTO product) {
        productDocumentRepository.save(mapToDocument(product));
    }

    public void deleteProduct(Long productId) {
        productDocumentRepository.deleteById(productId.toString());
    }

    private ProductSearchResult mapToResult(SearchHit<ProductDocument> hit) {
        ProductDocument document = hit.getContent();
        return new ProductSearchResult(
            document.getId(),
            document.getName(),
            document.getDescription(),
            document.getCategoryName(),
            document.getMinPrice(),
            document.getMaxPrice(),
            document.isInStock(),
            document.getImageUrls(),
            hit.getScore()
        );
    }

    private ProductDocument mapToDocument(ProductDTO product) {
        var document = new ProductDocument();
        document.setId(product.id().toString());
        document.setName(product.name());
        document.setDescription(product.description());
        document.setCategoryName(product.categoryName());
        document.setCategorySlug(product.categoryName() == null
            ? null
            : product.categoryName().toLowerCase().replace(' ', '-'));
        document.setInStock(product.variants().stream().anyMatch(variant -> variant.stock() > 0));
        document.setMinPrice(product.variants().stream()
            .map(VariantDTO::price)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO));
        document.setMaxPrice(product.variants().stream()
            .map(VariantDTO::price)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO));
        document.setStatus(product.status().name());
        document.setImageUrls(product.images().stream().map(image -> image.url()).toList());
        document.setSuggest(product.name());
        return document;
    }
}
