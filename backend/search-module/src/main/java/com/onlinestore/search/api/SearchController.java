package com.onlinestore.search.api;

import com.onlinestore.common.dto.PageResponse;
import com.onlinestore.search.dto.ProductSearchRequest;
import com.onlinestore.search.dto.ProductSearchResult;
import com.onlinestore.search.service.SearchService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/search")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "onlinestore.search.enabled", havingValue = "true", matchIfMissing = true)
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/products")
    public PageResponse<ProductSearchResult> search(@Valid ProductSearchRequest request, Pageable pageable) {
        return searchService.search(request, pageable);
    }

    @GetMapping("/suggest")
    public List<String> suggest(@RequestParam("q") String query) {
        return searchService.suggest(query);
    }
}
